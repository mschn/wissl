package fr.msch.wissl.util.exception;

/**
 * Exception class of the FileOragnizer module
 * 
 * @author alexandre.trovato@gmail.com
 *
 */
public class FileOrganizerException extends Exception {

	/** Version */
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor from an existing exception
	 * 
	 * @param message The message
	 * @param throwable The cause
	 */
	public FileOrganizerException(String message, Throwable throwable) {
		super(message, throwable);
	}

	/**
	 * Constructor from an existing exception
	 * 
	 * @param throwable The cause
	 */
	public FileOrganizerException(Throwable throwable) {
		super(throwable);
	}

	/**
	 * Constructor for a new exception
	 * 
	 * @param message The message
	 */
	public FileOrganizerException(String message) {
		super(message);
	}
}
