/* This file is part of Wissl - Copyright (C) 2013 Mathieu Schnoor
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.msch.wissl.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import fr.msch.wissl.common.Config;

/**
 * Simple and straightforward logging facility
 * <p>
 * Allows logging to a file and/or stdout, accessing through a singleton
 * pattern.
 * 
 * 
 * @author mathieu.schnoor@gmail.com
 *
 */
public final class Logger {

	/** File to which the log is appended */
	private PrintStream out;
	/** Path to the file to which the log is appended */
	private String outPath;

	/** incremented each time a line is appended to the log file */
	private int linesAppended;

	private final static String separator = System
			.getProperty("line.separator");

	/**
	 * Determines the category and importance of a log message
	 */
	public enum Level {
		/** a negative event that will most likely impact the execution */
		ERROR,
		/** a negative event that should not impact the execution */
		WARN,
		/** an event that should occur upon normal execution */
		INFO,
		/** additional information that should not be useful to end users */
		DEBUG;
	}

	/** print DEBUG level messages if true */
	private boolean debug = false;
	/** print trace in stdout messages */
	private boolean trace = false;
	/** trace max length */
	private int traceLen = 25;

	/**
	 * Visibility of a log event
	 */
	public enum Visibility {
		/** events should be appended to the log file */
		FILE,
		/** events should be printed on stdout */
		STDOUT,
		/** events should be printed in both file and stdout */
		BOTH,
		/** all events are dropped */
		NONE;

		/**
		 * @return true if this level of visibility outputs to a log file
		 */
		public boolean isFile() {
			return (this.equals(Visibility.FILE) || this
					.equals(Visibility.BOTH));
		}

		/**
		 * @return true if this level of visibility outputs to stdout
		 */
		public boolean isStdout() {
			return (this.equals(Visibility.STDOUT) || this
					.equals(Visibility.BOTH));
		}
	}

	private Visibility visibility;

	/** Singleton instance */
	private static Logger instance = null;

	/**
	 * Private constructor, access should be static only
	 * 
	 * @param output
	 * @param stdout
	 * @param debug
	 * @param trace
	 * @param stamps
	 * @param overwrite
	 * @param len
	 */
	private Logger(Visibility vis, String output, boolean debug, boolean trace,
			boolean overwrite, int tracelen) {
		this.outPath = output;
		this.visibility = vis;
		this.debug = debug;
		this.trace = trace;
		this.traceLen = tracelen;

		// log should append to file, trying to open a log file
		if (this.visibility.isFile()) {
			openLogFile(output, overwrite);
		}
	}

	/**
	 * Creates the logging facility
	 */
	public static void create() {
		Visibility vis = Visibility.NONE;

		boolean confFile = Config.isLogFileEnabled();
		boolean confOut = Config.isLogStdoutEnabled();

		if (confFile && confOut) {
			vis = Visibility.BOTH;
		} else if (confFile) {
			vis = Visibility.FILE;
		} else if (confOut) {
			vis = Visibility.STDOUT;
		}
		String out = Config.getLogFilePath();
		boolean debug = Config.isLogStdoutDebug();
		boolean trace = Config.isLogStdoutTrace();
		boolean overwrite = Config.isLogFileOverwrite();
		int len = Config.getLogTraceLength();

		Logger.instance = new Logger(vis, out, debug, trace, overwrite, len);
		instance.message("    Logging to: " + out, Level.INFO,
				Visibility.STDOUT);
		instance.message("--- Log begins --------", Level.INFO, Visibility.FILE);
	}

	private void openLogFile(String output, boolean overwrite) {
		try {
			// open the specified file
			this.out = new PrintStream(new FileOutputStream(new File(output),
					!overwrite));
		} catch (Exception e) {
			try {
				// could not open the specified file, trying a
				// temporary one instead
				this.outPath = System.getProperty("java.io.tmpdir")
						+ File.separator + "bifstk-"
						+ System.currentTimeMillis() + ".log";
				this.out = new PrintStream(new FileOutputStream(this.outPath));

				System.out.println("! Could not open log file '" + output
						+ "', appending log to " + this.outPath);
			} catch (Exception ex) {
				// log file could not be opened, disabling file output
				this.outPath = null;
				this.out = null;
				System.out
						.println("! Could not open log file '" + output + "'");
				if (this.visibility.isStdout()) {
					System.out.println("Logs will be printed on STDOUT only");
					this.visibility = Visibility.STDOUT;
				} else {
					System.out.println("All logs will be dropped");
					this.visibility = Visibility.NONE;
				}
			}
		}
	}

	/**
	 * Rotate logs after reaching a certain number of lines.
	 * the old log file is closed and moved and a new one is created.
	 */
	private void rotateFile() {
		if (this.linesAppended >= Config.getLogMaxlines()) {
			this.linesAppended = 0;

			this.out.close();

			File old = new File(this.outPath);
			File dir = old.getParentFile();
			File moved = new File(dir.getAbsolutePath() + File.separatorChar
					+ old.getName() + "-" + DateHelper.getCompactFormat());

			boolean ret = old.renameTo(moved);
			if (!ret) {
				openLogFile(this.outPath, false);
				Logger.error("Failed to rotate logs");
				Logger.debug("Target was: " + moved.getAbsolutePath());
			} else {
				openLogFile(this.outPath, false);
				this.out.println("Log has been rotated. Previous log file is "
						+ moved.getAbsolutePath());
			}
		} else {
			this.linesAppended++;
		}
	}

	/**
	 * Issue an error message
	 * 
	 * @param message the message to append to the log
	 */
	public static void error(String message) {
		instance.message("[E] " + message, Level.ERROR);
	}

	/**
	 * Issue an error message
	 * 
	 * @param message the message to append to the log
	 * @param t the Exception to append to the log
	 */
	public static void error(String message, Throwable t) {
		instance.message("[E] " + message, Level.ERROR, t);
	}

	/**
	 * Issue a warning message
	 * 
	 * @param message the message to append to the log
	 */
	public static void warn(String message) {
		instance.message("[W] " + message, Level.WARN);
	}

	/**
	 * Issue a warning message
	 * 
	 * @param t the Exception to append to the log
	 */
	public static void warn(String message, Throwable t) {
		instance.message("[W] " + message, Level.WARN, t);
	}

	/**
	 * Issue a debug message
	 * 
	 * @param message the message to append to the log
	 */
	public static void debug(String message) {
		instance.message("[D] " + message, Level.DEBUG);
	}

	/**
	 * Issue a debug message
	 * 
	 * @param message the message to append to the log
	 * @param t the Exception to append to the log
	 */
	public static void debug(String message, Throwable t) {
		instance.message("[D] " + message, Level.DEBUG, t);
	}

	/**
	 * Issue an information message
	 * 
	 * @param message the message to append to the log
	 */
	public static void info(String message) {
		instance.message("    " + message, Level.INFO);
	}

	/**
	 * Append a message to the log
	 * 
	 * @param message the message to print
	 * @param l the level of the message
	 */
	private void message(String message, Level l) {
		message(message, l, this.visibility);
	}

	/**
	 * Append a message to the log
	 * 
	 * @param message the message to print
	 * @param l the level of the message
	 * @param t the Exception to append to the log
	 */
	private void message(String message, Level l, Throwable t) {
		message(message, l, this.visibility, t);
	}

	/**
	 * Append a message to the log
	 * 
	 * @param message the message to print
	 * @param l the level of the message
	 * @param vis visibility of the message
	 * @param t the Exception to append to the log
	 */
	private void message(String message, Level l, Visibility vis) {
		message(message, l, vis, null);
	}

	private synchronized void message(String message, Level l, Visibility vis,
			Throwable t) {
		switch (vis) {
		case FILE:
			if (!this.visibility.isFile())
				return;
			break;
		case STDOUT:
			if (!this.visibility.isStdout())
				return;
			break;
		case NONE:
			return;
		case BOTH:
			if (!this.visibility.equals(Visibility.BOTH))
				return;
			break;
		}
		if (l.equals(Level.DEBUG) && !this.debug) {
			// debug is disabled
			return;
		}

		if (l.equals(Level.WARN) && !this.debug) {
			// do not print stack trace on WARN if not in DEBUG mode
			if (t != null) {
				message += " (" + t.getMessage() + ")";
				t = null;
			}
		}

		if (vis.isStdout()) {
			System.out.println(getFormattedString(message, t, false,
					this.trace, this.traceLen));
		}
		if (vis.isFile()) {
			rotateFile();
			this.out.println(getFormattedString(message, t, true, true,
					this.traceLen));
		}
	}

	private static String getFormattedString(String message, Throwable t,
			boolean stamps, boolean trace, int traceLen) {
		StringBuilder ret = new StringBuilder();
		String prefix = "";

		if (stamps) {
			prefix += DateHelper.getTimeStamps() + " ";
		}
		if (trace) {
			prefix += getContext(traceLen);
		}
		if (prefix.length() != 0) {
			prefix = "[" + prefix + "] ";
		}

		ret.append(prefix);
		ret.append(message);

		while (t != null) {
			String filler = new String();
			for (int i = 0; i < prefix.length() + 4; i++) {
				filler += " ";
			}
			ret.append(separator);
			ret.append(filler);
			ret.append(t.getClass().getName());
			if (t.getMessage() != null && t.getMessage().trim().length() > 0) {
				ret.append(": ");
				ret.append(t.getMessage());
			}

			for (StackTraceElement el : t.getStackTrace()) {
				ret.append(separator);
				ret.append(filler);
				ret.append("` ");
				ret.append(el.toString());
			}

			t = t.getCause();
		}
		return ret.toString();
	}

	/**
	 * Provides a String representing the caller method context : class and
	 * method. String is fixed length to improve readability
	 * 
	 * @param length length of the returned String
	 * @return a String representation of the caller method context
	 */
	private static String getContext(int length) {
		String context = "";
		try {
			StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			StackTraceElement e = null;
			int i = 4;
			while (true) {
				if (i >= stack.length) {
					break;
				}
				String sname = stack[i].getClassName();
				if (sname.matches(".*Log.*")) {
					i++;
					continue;
				} else {
					e = stack[i];
					break;
				}
			}
			if (e == null) {
				context = "...";
			} else {
				context = e.getClassName() + "#" + e.getMethodName();
			}
		} catch (Exception e) {
			context = "...";
		}

		if (length < 1) {
			throw new IllegalArgumentException("Less than " + length
					+ " characters would prove counter-productive,"
					+ "try a reasonnable value");
		}

		while (context.length() < length) {
			context += " ";
		}
		if (context.length() > length) {
			context = ".."
					+ context.substring(context.length() - length + 2,
							context.length());
		}

		return context;
	}
}
