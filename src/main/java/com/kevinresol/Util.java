package com.kevinresol;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

public class Util {

	public static void inheritIO(final InputStream src, final PrintStream dest) {
		new Thread(new Runnable() {
			public void run() {
				try (Scanner scanner = new Scanner(src)) {
					while (scanner.hasNextLine()) {
						dest.println(scanner.nextLine());
					}
				}
			}
		}).start();
	}
}
