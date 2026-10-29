package dev.rs.slava.icxc.musicstudio.cli;

/** Process exit codes of the CLI (architecture section 11). */
public final class ExitCode {

    /** Everything worked. */
    public static final int OK = 0;
    /** The command completed, but some items had errors (for example unreadable files). */
    public static final int ITEM_ERRORS = 1;
    /** Usage error or invalid input (unknown option, folder does not exist). */
    public static final int USAGE = 2;
    /** The operation was aborted because of an I/O failure, for example an unreadable root folder. */
    public static final int ABORTED = 3;
    /** Cancelled by the user (Ctrl-C), the shell convention 128 + SIGINT. */
    public static final int CANCELLED = 130;

    private ExitCode() {}
}
