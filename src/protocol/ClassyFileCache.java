package protocol;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 
 * @author Luke Kennedy and Andrew Hopkin
 */
public class ClassyFileCache {
	private static HashMap<String, ArrayList<byte[]>> cachedFiles = new HashMap<String, ArrayList<byte[]>>();

	public static ArrayList<byte[]> getFile(File file) {
		ArrayList<byte[]> fileData = cachedFiles.get(file.getAbsolutePath());
		if (fileData == null) {
			try {
				FileInputStream fileInStream;
				fileInStream = new FileInputStream(file);
				BufferedInputStream daFileStream = new BufferedInputStream(
						fileInStream, Protocol.CHUNK_LENGTH);
				fileData = new ArrayList<byte[]>();
				byte[] buffer = new byte[Protocol.CHUNK_LENGTH];
				while (daFileStream.read(buffer) != -1) {
					fileData.add(buffer);
				}
				cachedFiles.put(file.getAbsolutePath(), fileData);
				daFileStream.close();
				fileInStream.close();
			} catch (Exception e) {
				return null;
			}
		}
		return fileData;
	}
}
