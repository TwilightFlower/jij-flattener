package io.github.nuclearfarts.jijflattener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class Main {
	public static void main(String[] args) throws IOException {
		if(args.length == 1) {
			new JijFlattener(new File(args[0])).exec();
		} else if(args.length == 2) {
			new JijFlattener(new File(args[0]), Paths.get(args[1])).exec();
		} else if(args.length == 3) {
			new JijFlattener(new File(args[0]), Paths.get(args[1]), Paths.get(args[2])).exec();
		} else {
			System.err.println("Not enough/Too many arguments! Format: <input> [output] [work directory]");
		}
	}
}
