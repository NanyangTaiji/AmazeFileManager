package com.stealthcopter.networktools;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

public class IPTools {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$"
    );
    private static final Pattern IPV6_STD_PATTERN = Pattern.compile(
            "^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$"
    );
    private static final Pattern IPV6_HEX_COMPRESSED_PATTERN = Pattern.compile(
            "^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$"
    );

    /**
     * Answers if given string is a valid IPv4 address.
     */
    public static boolean isIPv4Address(String address) {
        return address != null && IPV4_PATTERN.matcher(address).matches();
    }

    /**
     * Answers if given string is a valid IPv6 address in long form.
     */
    public static boolean isIPv6StdAddress(String address) {
        return address != null && IPV6_STD_PATTERN.matcher(address).matches();
    }

    /**
     * Answers if given string is a valid IPv6 address in hex compressed form.
     */
    public static boolean isIPv6HexCompressedAddress(String address) {
        return address != null && IPV6_HEX_COMPRESSED_PATTERN.matcher(address).matches();
    }

    /**
     * Answers if given string is a valid IPv6 address.
     */
    public static boolean isIPv6Address(String address) {
        return address != null && (isIPv6StdAddress(address) || isIPv6HexCompressedAddress(address));
    }

    /**
     * Return the first local IPv4 address, or null if not found.
     */
    public static InetAddress getLocalIPv4Address() {
        List<InetAddress> localAddresses = getLocalIPv4Addresses();
        return localAddresses.isEmpty() ? null : localAddresses.get(0);
    }

    /**
     * Return a list of all IPv4 addresses found.
     */
    private static List<InetAddress> getLocalIPv4Addresses() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
                while (ifaceAddresses.hasMoreElements()) {
                    InetAddress addr = ifaceAddresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        addresses.add(addr);
                    }
                }
            }
        } catch (SocketException e) {
            // Handle exception
        }
        return addresses;
    }

    /**
     * Check if the provided IP address refers to the localhost.
     *
     * @param addr - address to check
     * @return - true if the IP address is the localhost
     */
    public static boolean isIpAddressLocalhost(InetAddress addr) {
        if (addr == null) {
            return false;
        }

        // Check if the address is a valid special local or loop back
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
            return true;
        }

        try {
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(addr);
            return networkInterface != null;
        } catch (SocketException e) {
            return false;
        }
    }

    /**
     * Check if the provided IP address belongs to the local network.
     *
     * @param addr - address to check
     * @return - true if the IP address is on the local network
     */
    public static boolean isIpAddressLocalNetwork(InetAddress addr) {
        return addr != null && addr.isSiteLocalAddress();
    }
}

