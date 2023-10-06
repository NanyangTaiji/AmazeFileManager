package com.amaze.filemanager.asynchronous.asynctasks.movecopy;
import static com.amaze.filemanager.fileoperations.filesystem.FolderState.CAN_CREATE_FILES;
import static com.amaze.filemanager.fileoperations.filesystem.OperationType.COPY;
import static com.amaze.filemanager.fileoperations.filesystem.OperationType.MOVE;

import android.app.ProgressDialog;
import android.content.Intent;
import android.view.LayoutInflater;
import android.widget.Toast;
import androidx.appcompat.widget.AppCompatCheckBox;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.amaze.filemanager.R;
import com.amaze.filemanager.asynchronous.asynctasks.Task;
import com.amaze.filemanager.asynchronous.asynctasks.movecopy.MoveFiles;
import com.amaze.filemanager.asynchronous.asynctasks.movecopy.MoveFilesTask;
import com.amaze.filemanager.asynchronous.management.ServiceWatcherUtil;
import com.amaze.filemanager.asynchronous.services.CopyService;
import com.amaze.filemanager.databinding.CopyDialogBinding;
import com.amaze.filemanager.fileoperations.filesystem.FolderState;
import com.amaze.filemanager.fileoperations.filesystem.OpenMode;
import com.amaze.filemanager.filesystem.FilenameHelper;
import com.amaze.filemanager.filesystem.HybridFile;
import com.amaze.filemanager.filesystem.HybridFileParcelable;
import com.amaze.filemanager.filesystem.MakeDirectoryOperation;
import com.amaze.filemanager.filesystem.files.FileUtils;
import com.amaze.filemanager.ui.activities.MainActivity;
import com.amaze.filemanager.utils.OnFileFound;
import com.amaze.filemanager.utils.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kotlin.Suppress;

/**
 * This helper class works by checking the conflicts during paste operation. After checking
 * conflicts [MaterialDialog] is shown to the user for each conflicting file. If the conflicting file
 * is a directory, the conflicts are resolved by inserting a node in [CopyNode] tree and then doing
 * BFS on this tree.
 */
public class PreparePasteTask {
    private String targetPath;
    private boolean isMove = false;
    private boolean isRootMode = false;
    private OpenMode openMode;
    private List<HybridFileParcelable> filesToCopy;

    private List<String> pathsList = new ArrayList<>();
    private List<ArrayList<HybridFileParcelable>> filesToCopyPerFolder = new ArrayList<>();

    private WeakReference<MainActivity> context;

   // @Suppress("DEPRECATION")
    private ProgressDialog progressDialog = null;

    private HybridFile destination;
    private List<HybridFileParcelable> conflictingFiles = new ArrayList<>();
    private Map<HybridFileParcelable, String> conflictingDirActionMap = new HashMap<>();

    private boolean skipAll = false;
    private boolean renameAll = false;
    private boolean overwriteAll = false;

    public PreparePasteTask(MainActivity strongRefMain) {
        context = new WeakReference<>(strongRefMain);
    }

    public void execute(
            String targetPath,
            boolean isMove,
            boolean isRootMode,
            OpenMode openMode,
            List<HybridFileParcelable> filesToCopy
    ) {
        this.targetPath = targetPath;
        this.isMove = isMove;
        this.isRootMode = isRootMode;
        this.openMode = openMode;
        this.filesToCopy = filesToCopy;

        boolean isCloudOrRootMode =
                openMode == OpenMode.OTG
                        || openMode == OpenMode.GDRIVE
                        || openMode == OpenMode.DROPBOX
                        || openMode == OpenMode.BOX
                        || openMode == OpenMode.ONEDRIVE
                        || openMode == OpenMode.ROOT;

        if (isCloudOrRootMode) {
            startService(filesToCopy, targetPath, openMode, isMove, isRootMode);
        }

        long totalBytes = FileUtils.getTotalBytes((ArrayList<HybridFileParcelable>) filesToCopy, context.get());

        destination = new HybridFile(openMode, targetPath);
        destination.generateMode(context.get());

        if (!filesToCopy.isEmpty() && isMove && filesToCopy.get(0).getParent(context.get()).equals(targetPath)) {
            Toast.makeText(context.get(), R.string.same_dir_move_error, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isMoveSupported =
                isMove && destination.getMode()== openMode && MoveFiles.getOperationSupportedFileSystem().contains(openMode);

        if (destination.getUsableSpace()< totalBytes && !isMoveSupported) {
            Toast.makeText(context.get(), R.string.in_safe, Toast.LENGTH_SHORT).show();
            return;
        }

      //  @Suppress("DEPRECATION")
                progressDialog = ProgressDialog.show(
                context.get(),
                "",
                context.get().getString(R.string.checking_conflicts)
        );

        checkConflicts(isRootMode, filesToCopy, destination, conflictingFiles, conflictingDirActionMap);
    }

    private void startService(
            List<HybridFileParcelable> sourceFiles,
            String target,
            OpenMode openMode,
            boolean isMove,
            boolean isRootMode
    ) {
        Intent intent = new Intent(context.get(), CopyService.class);
        intent.putParcelableArrayListExtra(CopyService.TAG_COPY_SOURCES, new ArrayList<>(sourceFiles));
        intent.putExtra(CopyService.TAG_COPY_TARGET, target);
        intent.putExtra(CopyService.TAG_COPY_OPEN_MODE, openMode.ordinal());
        intent.putExtra(CopyService.TAG_COPY_MOVE, isMove);
        intent.putExtra(CopyService.TAG_IS_ROOT_EXPLORER, isRootMode);
        ServiceWatcherUtil.runService(context.get(), intent);
    }

    private void checkConflicts(
            boolean isRootMode,
            List<HybridFileParcelable> filesToCopy,
            HybridFile destination,
            List<HybridFileParcelable> conflictingFiles,
            Map<HybridFileParcelable, String> conflictingDirActionMap
    ) {
        destination.forEachChildrenFile(
                context.get(),
                isRootMode,
                new OnFileFound() {
                    @Override
                    public void onFileFound(HybridFileParcelable file) {
                        for (HybridFileParcelable fileToCopy : filesToCopy) {
                            if (file.getName(context.get()).equals(fileToCopy.getName(context.get()))) {
                                conflictingFiles.add(fileToCopy);
                            }
                        }
                    }
                });

        prepareDialog(conflictingFiles, filesToCopy);

     //   @Suppress("DEPRECATION")
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        resolveConflict(conflictingFiles, conflictingDirActionMap, filesToCopy);
    }

    private void prepareDialog(
            List<HybridFileParcelable> conflictingFiles,
            List<HybridFileParcelable> filesToCopy
    ) {
        if (conflictingFiles.isEmpty()) return;

        MainActivity mainActivity = context.get();
        if (mainActivity == null) return;

        int accentColor = mainActivity.getAccent();
        MaterialDialog.Builder dialogBuilder = new MaterialDialog.Builder(mainActivity);
        CopyDialogBinding copyDialogBinding = CopyDialogBinding.inflate(LayoutInflater.from(mainActivity));
        dialogBuilder.customView(copyDialogBinding.getRoot(), true);
        AppCompatCheckBox checkBox = copyDialogBinding.checkBox;

        Utils.setTint(mainActivity, checkBox, accentColor);
        dialogBuilder.theme(mainActivity.getAppTheme().getMaterialDialogTheme(mainActivity));
        dialogBuilder.title(mainActivity.getResources().getString(R.string.paste));
        dialogBuilder.positiveText(R.string.rename);
        dialogBuilder.neutralText(R.string.skip);
        dialogBuilder.negativeText(R.string.overwrite);
        dialogBuilder.positiveColor(accentColor);
        dialogBuilder.negativeColor(accentColor);
        dialogBuilder.neutralColor(accentColor);

        showDialog(conflictingFiles, filesToCopy, copyDialogBinding, dialogBuilder, checkBox);
    }

    private void showDialog(
            List<HybridFileParcelable> conflictingFiles,
            List<HybridFileParcelable> filesToCopy,
            CopyDialogBinding copyDialogBinding,
            MaterialDialog.Builder dialogBuilder,
            AppCompatCheckBox checkBox
    ) {
        final Iterator<HybridFileParcelable> iterator = conflictingFiles.iterator();
        final HybridFileParcelable hybridFileParcelable = iterator.next();
        copyDialogBinding.fileNameText.setText(hybridFileParcelable.getName());
        MaterialDialog dialog = dialogBuilder.build();

        dialogBuilder.onPositive((dialog1, which) -> {
            if (checkBox.isChecked()) {
                renameAll = true;
            } else {
                conflictingDirActionMap.put(hybridFileParcelable, Action.RENAME);
                iterator.remove();
            }
            dialog1.dismiss();
            showDialog(conflictingFiles, filesToCopy, copyDialogBinding, dialogBuilder, checkBox);
        });

        dialogBuilder.onNegative((dialog1, which) -> {
            if (hybridFileParcelable.getParent(context.get()).equals(targetPath)) {
                Toast.makeText(context.get(), R.string.same_dir_overwrite_error, Toast.LENGTH_SHORT).show();
                if (checkBox.isChecked()) {
                    filesToCopy.removeAll(conflictingFiles);
                    conflictingFiles.clear();
                } else {
                    filesToCopy.remove(hybridFileParcelable);
                }
            } else if (checkBox.isChecked()) {
                overwriteAll = true;
            } else {
                conflictingDirActionMap.put(hybridFileParcelable, Action.OVERWRITE);
                iterator.remove();
            }
            dialog1.dismiss();
            showDialog(conflictingFiles, filesToCopy, copyDialogBinding, dialogBuilder, checkBox);
        });

        dialogBuilder.onNeutral((dialog1, which) -> {
            if (checkBox.isChecked()) {
                skipAll = true;
            } else {
                conflictingDirActionMap.put(hybridFileParcelable, Action.SKIP);
                iterator.remove();
            }
            dialog1.dismiss();
            showDialog(conflictingFiles, filesToCopy, copyDialogBinding, dialogBuilder, checkBox);
        });

        dialog.show();
    }


    private void resolveConflict(
            List<HybridFileParcelable> conflictingFiles,
            Map<HybridFileParcelable, String> conflictingDirActionMap,
            List<HybridFileParcelable> filesToCopy
    ) {
        int index = conflictingFiles.size() - 1;
        if (renameAll) {
            while (!conflictingFiles.isEmpty()) {
                conflictingDirActionMap.put(conflictingFiles.get(index), Action.RENAME);
                conflictingFiles.remove(index);
                index--;
            }
        } else if (overwriteAll) {
            while (!conflictingFiles.isEmpty()) {
                conflictingDirActionMap.put(conflictingFiles.get(index), Action.OVERWRITE);
                conflictingFiles.remove(index);
                index--;
            }
        } else if (skipAll) {
            while (!conflictingFiles.isEmpty()) {
                filesToCopy.remove(conflictingFiles.remove(index));
                index--;
            }
        }

        CopyNode rootNode = new CopyNode(targetPath, new ArrayList<>(filesToCopy));
        CopyNode currentNode = rootNode.startCopy();

        while (currentNode != null) {
            pathsList.add(currentNode.getPath());
            filesToCopyPerFolder.add(new ArrayList<>(currentNode.getFilesToCopy()));
            currentNode = rootNode.goToNextNode();
        }

        finishCopying();
    }

    private void finishCopying() {
        int index = 0;
        while (index < filesToCopyPerFolder.size()) {
            if (filesToCopyPerFolder.get(index).isEmpty()) {
                filesToCopyPerFolder.remove(index);
                pathsList.remove(index);
                index--;
            }
            index++;
        }

        if (!filesToCopyPerFolder.isEmpty()) {
            @FolderState.State
            int mode = context.get().mainActivityHelper.checkFolder(targetPath, openMode, context.get());
            if (mode == CAN_CREATE_FILES && !targetPath.contains("otg:/")) {
                context.get().oparrayListList = new ArrayList<>(filesToCopyPerFolder);
                context.get().oparrayList = null;
                context.get().operation = isMove ? MOVE : COPY;
                context.get().oppatheList = new ArrayList<>(pathsList);
            } else {
                if (!isMove) {
                    for (int foldersIndex = 0; foldersIndex < filesToCopyPerFolder.size(); foldersIndex++) {
                        startService(
                                new ArrayList<>(filesToCopyPerFolder.get(foldersIndex)),
                                pathsList.get(foldersIndex),
                                openMode,
                                isMove,
                                isRootMode
                        );
                    }
                } else {
                    Task.fromTask(
                            new MoveFilesTask(
                                    new ArrayList<>(filesToCopyPerFolder),
                                    isRootMode,
                                    targetPath,
                                    context.get(),
                                    openMode,
                                    new ArrayList<>(pathsList)
                            )
                    );
                }
            }
        } else {
            if (context.get() != null) {
                Toast.makeText(
                        context.get(),
                        context.get().getResources().getString(R.string.no_file_overwrite),
                        Toast.LENGTH_SHORT
                ).show();
            }
        }

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private class CopyNode {
        private String path;
        private List<HybridFileParcelable> filesToCopy;
        private List<CopyNode> nextNodes = new ArrayList<>();
        private LinkedList<CopyNode> queue;
        private Set<CopyNode> visited;

        CopyNode(String path, List<HybridFileParcelable> filesToCopy) {
            this.path = path;
            this.filesToCopy = new ArrayList<>(filesToCopy);

            Iterator<HybridFileParcelable> iterator = this.filesToCopy.iterator();
            while (iterator.hasNext()) {
                HybridFileParcelable hybridFileParcelable = iterator.next();
                if (conflictingDirActionMap.containsKey(hybridFileParcelable)) {
                    switch (conflictingDirActionMap.get(hybridFileParcelable)) {
                        case Action.RENAME:
                            if (hybridFileParcelable.isDirectory()) {
                                String newName =
                                        FilenameHelper.increment(hybridFileParcelable).getName(context.get());
                                String newPath = path + "/" + newName;
                                HybridFile hybridFile = new HybridFile(hybridFileParcelable.getMode(), newPath);
                                MakeDirectoryOperation.mkdirs(context.get(), hybridFile);
                                nextNodes.add(
                                        new CopyNode(
                                                newPath,
                                                hybridFileParcelable.listFiles(context.get(), isRootMode)
                                        )
                                );
                                iterator.remove();
                            } else {
                                filesToCopy.get(filesToCopy.indexOf(hybridFileParcelable)).setName(FilenameHelper.increment(hybridFileParcelable).getName(context.get()));
                                        ;
                            }
                            break;
                        case Action.SKIP:
                            iterator.remove();
                            break;
                    }
                }
            }
        }

        /**
         * Starts BFS traversal of tree.
         *
         * @return Root node
         */
        CopyNode startCopy() {
            queue = new LinkedList<>();
            visited = new HashSet<>();
            queue.add(this);
            visited.add(this);
            return this;
        }

        /**
         * Moves to the next unvisited node in the tree.
         *
         * @return The next unvisited node if available, otherwise returns null.
         */
        CopyNode goToNextNode() {
            if (queue == null || queue.isEmpty()) return null;

            CopyNode node = queue.element();
            CopyNode child = getUnvisitedChildNode(node);
            if (child != null) {
                visited.add(child);
                queue.add(child);
                return child;
            } else {
                queue.remove();
                return goToNextNode();
            }
        }

        private CopyNode getUnvisitedChildNode(CopyNode node) {
            for (CopyNode currentNode : node.nextNodes) {
                if (!visited.contains(currentNode)) {
                    return currentNode;
                }
            }
            return null;
        }

        String getPath() {
            return path;
        }

        List<HybridFileParcelable> getFilesToCopy() {
            return filesToCopy;
        }
    }

    private static class Action {
        static final String SKIP = "skip";
        static final String RENAME = "rename";
        static final String OVERWRITE = "overwrite";
    }
}

