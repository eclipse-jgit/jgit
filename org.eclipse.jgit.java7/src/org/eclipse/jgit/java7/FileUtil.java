package org.eclipse.jgit.java7;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
import java.text.Normalizer.Form;

import org.eclipse.jgit.util.SystemReader;

class FileUtil {

	static String readSymlink(File path) throws IOException {
		Path nioPath = path.toPath();
		Path target = Files.readSymbolicLink(nioPath);
		String targetString = target.toString();
		if (SystemReader.getInstance().isWindows())
			targetString = targetString.replace('\\', '/');
		else if (SystemReader.getInstance().isMacOS()) {
			targetString = Normalizer.normalize(targetString, Form.NFC);
		}
		return targetString;
	}

	public static void createSymLink(File path, String target)
			throws IOException {
		Path nioPath = path.toPath();
		if (SystemReader.getInstance().isWindows())
			target = target.replace('/', '\\');
		Path nioTarget = new File(target).toPath();
		Files.createSymbolicLink(nioPath, nioTarget);
	}

	public static boolean isSymlink(File path) {
		Path nioPath = path.toPath();
		return Files.isSymbolicLink(nioPath);
	}

	public static long lastModified(File path) throws IOException {
		Path nioPath = path.toPath();
		return Files.getLastModifiedTime(nioPath, LinkOption.NOFOLLOW_LINKS)
				.toMillis();
	}

	public static void setLastModified(File path, long time) throws IOException {
		Path nioPath = path.toPath();
		Files.setLastModifiedTime(nioPath, FileTime.fromMillis(time));
	}

	public static boolean exists(File path) {
		Path nioPath = path.toPath();
		return Files.exists(nioPath, LinkOption.NOFOLLOW_LINKS);
	}

	public static boolean isHidden(File path) throws IOException {
		Path nioPath = path.toPath();
		return Files.isHidden(nioPath);
	}

	public static void setHidden(File path, boolean hidden) throws IOException {
		Path nioPath = path.toPath();
		Files.setAttribute(nioPath, "dos:hidden", Boolean.valueOf(hidden),
				LinkOption.NOFOLLOW_LINKS);
	}

	public static long getLength(File path) throws IOException {
		Path nioPath = path.toPath();
		if (Files.isSymbolicLink(nioPath))
			return Files.readSymbolicLink(nioPath).toString().length();
		return Files.size(nioPath);
	}

	public static boolean isDirectory(File path) {
		Path nioPath = path.toPath();
		return Files.isDirectory(nioPath, LinkOption.NOFOLLOW_LINKS);
	}

	public static boolean isFile(File path) {
		Path nioPath = path.toPath();
		return Files.isRegularFile(nioPath, LinkOption.NOFOLLOW_LINKS);
	}

	public static boolean canExecute(File path) {
		if (!isFile(path))
			return false;
		return path.canExecute();
	}

	public static boolean setExecute(File path, boolean executable) {
		if (!isFile(path))
			return false;
		return path.setExecutable(executable);
	}

}
