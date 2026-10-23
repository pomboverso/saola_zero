package com.rama.okapi_zero.helpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;

/**
 * Talks to the kernel USB gadget (configfs) through a root shell.
 * Requires root (Magisk / KernelSU) and a kernel with CONFIG_USB_CONFIGFS_F_HID.
 * All methods block: call them from a background thread.
 */
public final class HidGadget {
    private HidGadget() {
    }

    public static final String HID_DEV = "/dev/hidg0";
    private static final long SU_TIMEOUT_MS = 30000;

    /** Read-only report of everything relevant to HID gadget support. */
    public static String diagnose() {
        String s = ""
                + "echo '== su'; id\n"
                + "echo '== kernel'; uname -r\n"
                + "echo '== selinux'; getenforce\n"
                + "echo '== usb props'; getprop sys.usb.state; getprop sys.usb.config; getprop sys.usb.controller\n"
                + "echo '== kernel config (HID gadget)'; zcat /proc/config.gz 2>&1 | grep -E 'USB_F_HID|CONFIGFS_F_HID|USB_CONFIGFS=' \n"
                + "echo '== loaded modules'; grep -i f_hid /proc/modules\n"
                + "echo '== module files'; find /vendor_dlkm /system_dlkm /vendor/lib/modules /lib/modules -name '*f_hid*' 2>/dev/null\n"
                + "echo '== configfs'; ls /config/usb_gadget 2>&1\n"
                + "for g in /config/usb_gadget/*; do\n"
                + "  echo \"-- gadget $g\"; echo \"UDC: $(cat $g/UDC)\"\n"
                + "  echo 'functions:'; ls $g/functions\n"
                + "  for c in $g/configs/*; do echo \"config $c:\"; ls $c; done\n"
                + "done\n"
                + "echo '== /dev/hidg*'; ls -l /dev/hidg* 2>&1\n";
        return runAsRoot(s);
    }

    /** Adds a HID keyboard function to the existing gadget and re-binds it. */
    public static String setup() {
        String desc = HidKeys.toOctalEscapes(HidKeys.descriptorBytes());
        String s = ""
                + "G=$(ls -d /config/usb_gadget/*/ 2>/dev/null | head -n 1); G=${G%/}\n"
                + "if [ -z \"$G\" ]; then echo 'ERROR: no gadget in /config/usb_gadget (configfs missing?)'; exit 1; fi\n"
                + "echo \"gadget: $G\"\n"
                + "UDC=$(cat $G/UDC); [ -z \"$UDC\" ] && UDC=$(getprop sys.usb.controller)\n"
                + "echo \"udc: $UDC\"\n"
                + "grep -q f_hid /proc/modules || modprobe usb_f_hid 2>/dev/null\n"
                + "echo '' > $G/UDC\n"
                + "F=$G/functions/hid.usb0\n"
                + "[ -d $F ] || mkdir $F || { echo 'ERROR: mkdir hid.usb0 failed: kernel has no HID gadget function'; echo \"$UDC\" > $G/UDC; exit 1; }\n"
                + "echo 1 > $F/protocol\n"
                + "echo 1 > $F/subclass\n"
                + "echo " + HidKeys.REPORT_LENGTH + " > $F/report_length\n"
                + "printf '" + desc + "' > $F/report_desc\n"
                + "C=$(ls -d $G/configs/*/ | head -n 1); C=${C%/}\n"
                + "[ -e $C/hid.usb0 ] || ln -s $F $C/hid.usb0\n"
                + "echo \"$UDC\" > $G/UDC\n"
                + "sleep 1\n"
                + "echo \"UDC now: $(cat $G/UDC)\"\n"
                + "ls -l /dev/hidg* 2>&1\n";
        return runAsRoot(s);
    }

    /** Sends the text as keystrokes. Returns shell output (empty on success). */
    public static String type(String text) {
        List<byte[]> reports = HidKeys.reportsFor(text);
        StringBuilder s = new StringBuilder();
        s.append("[ -c ").append(HID_DEV).append(" ] || { echo 'ERROR: cannot open ").append(HID_DEV).append("'; exit 1; }\n");
        s.append("exec 3>").append(HID_DEV).append("\n");
        for (byte[] r : reports) {
            s.append("printf '").append(HidKeys.toOctalEscapes(r)).append("' >&3 || { echo 'ERROR: write failed (cable/host not ready?)'; exit 1; }\n");
        }
        s.append("exec 3>&-\n");
        return runAsRoot(s.toString());
    }

    private static String runAsRoot(String script) {
        final Process p;
        try {
            p = Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            return "ERROR: cannot start su (" + e.getMessage() + "). Is the device rooted?";
        }
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(SU_TIMEOUT_MS);
                p.destroy();
            } catch (InterruptedException ignored) {
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();
        StringBuilder out = new StringBuilder();
        try {
            OutputStream os = p.getOutputStream();
            os.write((script + "\nexit\n").getBytes("UTF-8"));
            os.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream(), "UTF-8"));
            // stderr is read after stdout; fine for these short scripts.
            String line;
            while ((line = in.readLine()) != null) out.append(line).append('\n');
            while ((line = err.readLine()) != null) out.append(line).append('\n');
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            out.append("ERROR: ").append(e.getMessage());
        } finally {
            watchdog.interrupt();
            p.destroy();
        }
        return out.toString().trim();
    }
}
