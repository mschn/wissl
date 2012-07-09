package fr.msch.wissl.util;

import java.util.Comparator;

/**
 * Comparator class : inverse order and sort by string length desc
 * 
 * @author alexandre.trovato@gmail.com
 * 
 */
public class LibraryPathComparator implements Comparator<String> {

	/**
	 * Compare both string
	 */
	@Override
	public int compare(String s1, String s2) {
		return 0 - s1.compareTo(s2) - s1.length() + s2.length();
	}

}
