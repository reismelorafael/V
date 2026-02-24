package com.vectras.vm.utils;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.vectras.qemu.Config;
import com.vectras.vm.R;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 
 * @author dev
 */
public class FileUtils {
	public static final String TAG = "FileUtils";

	@NonNull
	public static File getExternalFilesDirectory(Context context) {
		return new File(Environment.getExternalStorageDirectory(), "Documents/VectrasVM");
	}

	public static void chmod(File file, int mode) {
		try {
			Os.chmod(file.getAbsolutePath(), mode);
		}
		catch (ErrnoException e) {}
	}

	private static Uri contentUri = null;

	@SuppressLint("NewApi")
	public static String getPath(Context context, final Uri uri) {
		// check here to KITKAT or new version
		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
		String selection = null;
		String[] selectionArgs = null;
		// DocumentProvider
		if (isKitKat ) {
			// ExternalStorageProvider

			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				String fullPath = getPathFromExtSD(split);
				if (fullPath != "") {
					return fullPath;
				} else {
					return null;
				}
			}


			// DownloadsProvider

			if (isDownloadsDocument(uri)) {

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					final String id;
					Cursor cursor = null;
					try {
						cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
						if (cursor != null && cursor.moveToFirst()) {
							String fileName = cursor.getString(0);
							String path = Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName;
							if (!TextUtils.isEmpty(path)) {
								return path;
							}
						}
					}
					finally {
						if (cursor != null)
							cursor.close();
					}
					id = DocumentsContract.getDocumentId(uri);
					if (!TextUtils.isEmpty(id)) {
						if (id.startsWith("raw:")) {
							return id.replaceFirst("raw:", "");
						}
						String[] contentUriPrefixesToTry = new String[]{
								"content://downloads/public_downloads",
								"content://downloads/my_downloads"
						};
						for (String contentUriPrefix : contentUriPrefixesToTry) {
							try {
								final Uri contentUri = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), Long.valueOf(id));


								return getDataColumn(context, contentUri, null, null);
							} catch (NumberFormatException e) {
								//In Android 8 and Android P the id is not a number
								return uri.getPath().replaceFirst("^/document/raw:", "").replaceFirst("^raw:", "");
							}
						}


					}
				}
				else {
					final String id = DocumentsContract.getDocumentId(uri);

					if (id.startsWith("raw:")) {
						return id.replaceFirst("raw:", "");
					}
					try {
						contentUri = ContentUris.withAppendedId(
								Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
					}
					catch (NumberFormatException e) {
						e.printStackTrace();
					}
					if (contentUri != null) {

						return getDataColumn(context, contentUri, null, null);
					}
				}
			}


			// MediaProvider
			if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;

				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}
				selection = "_id=?";
				selectionArgs = new String[]{split[1]};


				return getDataColumn(context, contentUri, selection,
						selectionArgs);
			}

			if (isGoogleDriveUri(uri)) {
				return getDriveFilePath(context, uri);
			}

			if(isWhatsAppFile(uri)){
				return getFilePathForWhatsApp(context, uri);
			}


			if ("content".equalsIgnoreCase(uri.getScheme())) {

				if (isGooglePhotosUri(uri)) {
					return uri.getLastPathSegment();
				}
				if (isGoogleDriveUri(uri)) {
					return getDriveFilePath(context, uri);
				}
				if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
				{

					// return getFilePathFromURI(context,uri);
					return copyFileToInternalStorage(context, uri,"userfiles");
					// return getRealPathFromURI(context,uri);
				}
				else
				{
					return getDataColumn(context, uri, null, null);
				}

			}
			if ("file".equalsIgnoreCase(uri.getScheme())) {
				return uri.getPath();
			}
		}
		else {

			if(isWhatsAppFile(uri)){
				return getFilePathForWhatsApp(context, uri);
			}

			if ("content".equalsIgnoreCase(uri.getScheme())) {
				String[] projection = {
						MediaStore.Images.Media.DATA
				};
				Cursor cursor = null;
				try {
					cursor = context.getContentResolver()
							.query(uri, projection, selection, selectionArgs, null);
					int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
					if (cursor.moveToFirst()) {
						return cursor.getString(column_index);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}

	private static boolean fileExists(String filePath) {
		File file = new File(filePath);

		return file.exists();
	}

	private static String getPathFromExtSD(String[] pathData) {
		final String type = pathData[0];
		final String relativePath = "/" + pathData[1];
		String fullPath = "";

		// on my Sony devices (4.4.4 & 5.1.1), `type` is a dynamic string
		// something like "71F8-2C0A", some kind of unique id per storage
		// don't know any API that can get the root path of that storage based on its id.
		//
		// so no "primary" type, but let the check here for other devices
		if ("primary".equalsIgnoreCase(type)) {
			fullPath = Environment.getExternalStorageDirectory() + relativePath;
			if (fileExists(fullPath)) {
				return fullPath;
			}
		}

		// Environment.isExternalStorageRemovable() is `true` for external and internal storage
		// so we cannot relay on it.
		//
		// instead, for each possible path, check if file exists
		// we'll start with secondary storage as this could be our (physically) removable sd card
		fullPath = System.getenv("SECONDARY_STORAGE") + relativePath;
		if (fileExists(fullPath)) {
			return fullPath;
		}

		fullPath = System.getenv("EXTERNAL_STORAGE") + relativePath;
		if (fileExists(fullPath)) {
			return fullPath;
		}

		return fullPath;
	}

	private static String getDriveFilePath(Context context, Uri uri) {
		Uri returnUri = uri;
		Cursor returnCursor = context.getContentResolver().query(returnUri, null, null, null, null);
		if (returnCursor == null) {
			return null;
		}
		/*
		 * Get the column indexes of the data in the Cursor,
		 *     * move to the first row in the Cursor, get the data,
		 *     * and display it.
		 * */
		try {
			int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
			int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
			returnCursor.moveToFirst();
			String name = (returnCursor.getString(nameIndex));
			String size = (Long.toString(returnCursor.getLong(sizeIndex)));
			File file = new File(context.getCacheDir(), name);
			try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
				 FileOutputStream outputStream = new FileOutputStream(file)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = inputStream.read(buffer)) > 0) {
					outputStream.write(buffer, 0, read);
				}
			}
			Log.e("File Size", "Size " + file.length());
			Log.e("File Path", "Path " + file.getPath());
			Log.e("File Size", "Size " + file.length());
			return file.getPath();
		} catch (Exception e) {
			Log.e("Exception", e.getMessage());
			return null;
		} finally {
			returnCursor.close();
		}
	}

	/***
	 * Used for Android Q+
	 * @param uri
	 * @param newDirName if you want to create a directory, you can set this variable
	 * @return
	 */
	private static String copyFileToInternalStorage(Context context, Uri uri, String newDirName) {
		Uri returnUri = uri;

		Cursor returnCursor = context.getContentResolver().query(returnUri, new String[]{
				OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE
		}, null, null, null);


		/*
		 * Get the column indexes of the data in the Cursor,
		 *     * move to the first row in the Cursor, get the data,
		 *     * and display it.
		 * */
		int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
		int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
		returnCursor.moveToFirst();
		String name = (returnCursor.getString(nameIndex));
		String size = (Long.toString(returnCursor.getLong(sizeIndex)));

		File output;
		if(!newDirName.equals("")) {
			File dir = new File(context.getFilesDir() + "/" + newDirName);
			if (!dir.exists()) {
				dir.mkdir();
			}
			output = new File(context.getFilesDir() + "/" + newDirName + "/" + name);
		}
		else{
			output = new File(context.getFilesDir() + "/" + name);
		}
		try {
			InputStream inputStream = context.getContentResolver().openInputStream(uri);
			FileOutputStream outputStream = new FileOutputStream(output);
			int read = 0;
			int bufferSize = 1024;
			final byte[] buffers = new byte[bufferSize];
			while ((read = inputStream.read(buffers)) != -1) {
				outputStream.write(buffers, 0, read);
			}

			inputStream.close();
			outputStream.close();

		}
		catch (Exception e) {

			Log.e("Exception", e.getMessage());
		}

		return output.getPath();
	}

	private static String getFilePathForWhatsApp(Context context, Uri uri){
		return  copyFileToInternalStorage(context, uri,"whatsapp");
	}

	private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
		if (uri == null) return null;

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = {column};

		try {
			cursor = context.getContentResolver().query(uri, projection,
					selection, selectionArgs, null);

			if (cursor != null && cursor.moveToFirst()) {
				final int index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(index);
			}
		}
		finally {
			if (cursor != null)
				cursor.close();
		}

		return null;
	}

	private static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	private static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	private static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	private static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	public static boolean isWhatsAppFile(Uri uri){
		return "com.whatsapp.provider.media".equals(uri.getAuthority());
	}

	private static boolean isGoogleDriveUri(Uri uri) {
		return "com.google.android.apps.docs.storage".equals(uri.getAuthority()) || "com.google.android.apps.docs.storage.legacy".equals(uri.getAuthority());
	}


	public static String loadTextFile(Activity activity, String fileName, boolean loadFromRawFolder) throws IOException {
		InputStream iS;
		if (loadFromRawFolder) {
			int rID = activity.getResources().getIdentifier(fileName, "raw", activity.getPackageName());
			iS = activity.getResources().openRawResource(rID);
		} else {
			iS = activity.getResources().getAssets().open(fileName);
		}

		ByteArrayOutputStream oS = new ByteArrayOutputStream();
		byte[] buffer = new byte[Math.max(1024, iS.available())];
		int bytesRead;
		while ((bytesRead = iS.read(buffer)) > 0) {
			oS.write(buffer, 0, bytesRead);
		}
		oS.close();
		iS.close();

		return oS.toString();
	}

	public String LoadFile(Activity activity, String fileName, boolean loadFromRawFolder) throws IOException {
		return loadTextFile(activity, fileName, loadFromRawFolder);
	}

	public static void saveFileContents(String dBFile, String machinesToExport) throws IOException {
		byteArrayToFile(machinesToExport.getBytes(), new File(dBFile));
	}

	public static void byteArrayToFile(byte[] byteData, File filePath) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(filePath)) {
			fos.write(byteData);
		} catch (IOException ex) {
			Log.e(TAG, "Failed to write byte array to file: " + filePath, ex);
			throw ex;
		}
	}

	public static String getDataDir(Context context) {

		String dataDir = context.getApplicationInfo().dataDir;
		PackageManager m = context.getPackageManager();
		String packageName = context.getPackageName();
		Log.v("VMExecutor", "Found packageName: " + packageName);

		if (dataDir == null) {
			dataDir = "/data/data/" + packageName;
		}
		return dataDir;
	}

	public static boolean fileValid(Context context, String path) {
		return fileValid(context, path, null);
	}

	public static boolean fileValid(Context context, String path, String backendMode) {

		if (path == null || path.equals(""))
			return true;
		if (path.startsWith("content://") || path.startsWith("/content/")) {
			String normalizedPath = path.replaceFirst("^/content", "content:");
			ParcelFileDescriptor pfd = null;
			try {
				String mode = resolveContentOpenMode(normalizedPath, backendMode);
				pfd = context.getContentResolver().openFileDescriptor(Uri.parse(normalizedPath), mode);
				return pfd != null;
			} catch (Exception e) {
				Log.e(TAG, "Failed to validate file path: " + path, e);
				return false;
			} finally {
				if (pfd != null) {
					try {
						pfd.close();
					} catch (IOException e) {
						Log.w(TAG, "Failed to close file descriptor during validation: " + path, e);
					}
				}
			}
		} else {
			File file = new File(path);
			return file.exists();
		}
	}

	public static HashMap<Integer, ParcelFileDescriptor> fds = new HashMap<Integer, ParcelFileDescriptor>();
	private static final HashMap<Integer, String> fdOwners = new HashMap<Integer, String>();

	public static int get_fd(final Context context, String path) {
		return get_fd(context, path, null);
	}

	public static int get_fd(final Context context, String path, String backendMode) {
		int fd = 0;
		if (path == null)
			return 0;

		if (path.startsWith("/content") || path.startsWith("content://")) {
			path = path.replaceFirst("/content", "content:");

			try {
				String mode = resolveContentOpenMode(path, backendMode);
				ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(Uri.parse(path), mode);
				fd = pfd.getFd();
				registerFd(fd, pfd);
			} catch (final FileNotFoundException e) {
				Log.e(TAG, "Failed to open content URI: " + path, e);
				new Handler(Looper.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(context, "Error: " + e, Toast.LENGTH_SHORT).show();
					}
				});
			}
		} else {
			try {
				int mode = resolveParcelOpenMode(path, backendMode);
				File file = new File(path);
				if (!file.exists())
					file.createNewFile();
				ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, mode);
				fd = pfd.getFd();
				registerFd(fd, pfd);
			} catch (Exception e) {
				Log.e(TAG, "Failed to open file: " + path, e);
			}

		}
		return fd;
	}

	public static String resolveContentOpenMode(String path, String backendMode) {
		if (isIsoPath(path)) {
			return "r";
		}

		String normalizedBackendMode = normalizeBackendMode(backendMode);
		if ("r".equals(normalizedBackendMode) || "w".equals(normalizedBackendMode)
				|| "rw".equals(normalizedBackendMode) || "wt".equals(normalizedBackendMode)
				|| "wa".equals(normalizedBackendMode)) {
			return normalizedBackendMode;
		}

		return "rw";
	}

	public static int resolveParcelOpenMode(String path, String backendMode) {
		if (isIsoPath(path)) {
			return ParcelFileDescriptor.MODE_READ_ONLY;
		}

		String normalizedBackendMode = normalizeBackendMode(backendMode);
		if ("r".equals(normalizedBackendMode)) {
			return ParcelFileDescriptor.MODE_READ_ONLY;
		}

		return ParcelFileDescriptor.MODE_READ_WRITE;
	}

	private static String normalizeBackendMode(String backendMode) {
		return backendMode == null ? "" : backendMode.trim().toLowerCase();
	}

	private static boolean isIsoPath(String path) {
		return path != null && path.toLowerCase().endsWith(".iso");
	}

	public static int close_fd(int fd) {

		if (FileUtils.fds.containsKey(fd)) {
			ParcelFileDescriptor pfd = FileUtils.fds.get(fd);
			try {
				pfd.close();
				FileUtils.fds.remove(fd);
				fdOwners.remove(fd);
				return 0; // success for Native side
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		return -1;
	}

	public static synchronized void closeFdsForVm(String vmId) {
		if (vmId == null || vmId.trim().isEmpty()) {
			return;
		}

		Set<Integer> ownedFds = new HashSet<Integer>();
		for (Integer fd : fdOwners.keySet()) {
			if (vmId.equals(fdOwners.get(fd))) {
				ownedFds.add(fd);
			}
		}

		for (Integer fd : ownedFds) {
			close_fd(fd);
		}
	}

	public static synchronized void closeAllFds() {
		Integer[] fdKeys = fds.keySet().toArray(new Integer[0]);
		for (Integer fd : fdKeys) {
			close_fd(fd);
		}
	}

	private static synchronized void registerFd(int fd, ParcelFileDescriptor pfd) {
		if (fd <= 0 || pfd == null) {
			return;
		}
		fds.put(fd, pfd);
		String vmId = Config.vmID == null ? "" : Config.vmID.trim();
		if (!vmId.isEmpty()) {
			fdOwners.put(fd, vmId);
		}
	}

	public static void writeToFile(String data, File file, Context context) {
		try {
			FileOutputStream fileOutStream = new FileOutputStream(file);
			OutputStreamWriter outputWriter = new OutputStreamWriter(fileOutStream);
			outputWriter.write(data);
			outputWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String readFromFile(Context context, File file) {
		String contents = null;
		try {
			int length = (int) file.length();

			byte[] bytes = new byte[length];

			FileInputStream in = new FileInputStream(file);
			try {
				in.read(bytes);
			} finally {
				in.close();
			}

			contents = new String(bytes);
		} catch (Exception e) {
			UIUtils.toastLong(context, e.toString());
			return "error";
		}
		return contents;
	}

	public static boolean moveFile(String oldfilename, String newFolderPath, String newFilename) {
		File folder = new File(newFolderPath);
		if (!folder.exists())
		    folder.mkdirs();

		File oldfile = new File(oldfilename);
		File newFile = new File(newFolderPath, newFilename);

		if (!newFile.exists()) {
            try {
                newFile.createNewFile();
            } catch (IOException e) {
                Log.e(TAG, "Failed to create file: " + newFile.getAbsolutePath(), e);
            }
        }
		return oldfile.renameTo(newFile);
	}

	public static boolean isFileExists(String filePath) {
		File file = new File(filePath.replaceAll("\n", ""));
		return file.exists();
	}

	public static void moveAFile(String _from, String _to) {
		File oldFile = new File(_from);
		File newFile = new File(_to);

		boolean success = oldFile.renameTo(newFile);
		if (success) {
			Log.d("File", "Done!");
		} else {
			Log.e("File", "Failed!");
		}
	}

	public static void copyAFile(String _sourceFile, String _destFile) {
		File vDir = new File(_destFile.substring((int)0, (int)(_destFile.lastIndexOf("/"))));
		if (!vDir.exists()) {
			vDir.mkdirs();
		}
		try {
			File source = new File(_sourceFile);
			File dest = new File(_destFile);

			if (!source.exists())
			{
				throw new IOException("Source file not found");
			}

			FileInputStream inStream = new FileInputStream(source);
			FileOutputStream outStream = new FileOutputStream(dest);

			byte[] buffer = new byte[1024];
			int length;
			while ((length = inStream.read(buffer))
					> 0) {
				outStream.write(buffer, 0, length);
			}

			inStream.close();
			outStream.close();
		} catch (IOException e) {

		}

	}

	public static void copyFileFromUri(Context context, Uri sourceUri, String destFile) throws IOException {
		copyFileFromUri(context, sourceUri, new File(destFile));
	}

	public static void copyFileFromUri(Context context, Uri sourceUri, File destFile) throws IOException {
		File parent = Objects.requireNonNull(destFile.getParentFile());
		if (!parent.exists() && !parent.mkdirs()) {
			throw new IOException("Unable to create destination folder: " + parent);
		}

		try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
			 OutputStream outputStream = new FileOutputStream(destFile)) {
			if (inputStream == null) {
				throw new IOException("Unable to open source URI: " + sourceUri);
			}
			byte[] buffer = new byte[32 * 1024];
			if (DeviceUtils.totalMemoryCapacity(context) < 3L * 1024 * 1024 * 1024) {
				buffer = new byte[4 * 1024];
			} else if (DeviceUtils.totalMemoryCapacity(context) < 5L * 1024 * 1024 * 1024) {
				buffer = new byte[8 * 1024];
			} else if (DeviceUtils.totalMemoryCapacity(context) < 7L * 1024 * 1024 * 1024) {
				buffer = new byte[16 * 1024];
			}
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
			outputStream.flush();
		}
	}

	public static File resolveSafeDestinationFile(File allowedRoot, String displayName) throws IOException {
		String safeName = SafeFileName.normalizeFromDisplayName(displayName);
		File rootCanonical = allowedRoot.getCanonicalFile();
		if (!rootCanonical.exists() && !rootCanonical.mkdirs()) {
			throw new IOException("Unable to create destination root: " + rootCanonical);
		}

		File destCanonical = new File(rootCanonical, safeName).getCanonicalFile();
		String rootPath = rootCanonical.getPath();
		String destPath = destCanonical.getPath();
		if (!destPath.equals(rootPath) && !destPath.startsWith(rootPath + File.separator)) {
			throw new SecurityException("Destination escapes allowed root.");
		}
		return destCanonical;
	}

	public static String getFileNameFromUri(Context context, Uri uri) {
		String result = null;

		Cursor cursor = context.getContentResolver().query(
				uri,
				null,
				null,
				null,
				null
		);

		try {
			if (cursor != null && cursor.moveToFirst()) {
				int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				result = cursor.getString(nameIndex);
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}

		if (result == null) {
			result = uri.getLastPathSegment();
		}

		return result;
	}

	public static void deleteDirectory(String _pathToDelete) {
		File _dir = new File(_pathToDelete);
		if (_dir.isDirectory()) {
			String[] children = _dir.list();

			if (children == null) {
				Log.e("ERROR", "Deletion failed. " + _dir);
				return;
			}

			for (int i = 0; i < children.length; i++) {
				File temp = new File(_dir, children[i]);
				deleteDirectory(String.valueOf(temp));
			}
		}
		boolean success = _dir.delete();
		if (!success) {
			Log.e("ERROR", "Deletion failed. " + _dir);
		}
	}

    public static boolean canRead(String filePath) {
        File file = new File(filePath);
        return file.canRead();
    }

	public static String readAFile(String filePath) {
		StringBuilder content = new StringBuilder();
		try (FileInputStream inputStream = new FileInputStream(filePath);
			 BufferedReader reader = new BufferedReader(new
					 InputStreamReader(inputStream))) {
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line).append("\n");
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return content.toString();

	}

	public static boolean writeToFile(String folderPath, String fileName, String content) {
		File vDir = new File(folderPath);
		if (!vDir.exists()) {
			if (!vDir.mkdirs()) return false;
		}
		File file = new File(folderPath, fileName);
		FileOutputStream outputStream;
		try {
			outputStream = new FileOutputStream(file);
			outputStream.write(content.getBytes());
			outputStream.close();
		} catch (IOException e) {
			Log.e(TAG, "writeToFile: ", e);
			return false;
		}
		return true;
	}

	public static void getAListOfAllFilesAndFoldersInADirectory(String path, ArrayList<String> list) {
		File dir = new File(path);
		if (!dir.exists() || dir.isFile()) return;

		File[] listFiles = dir.listFiles();
		if (listFiles == null || listFiles.length <= 0) return;

		if (list == null) return;
		list.clear();
		for (File file : listFiles) {
			list.add(file.getAbsolutePath());
		}
	}

	public static int getFileSize(String _path) {
		try {
			File file = new File(_path);
			if (!file.exists()) {
				return 0;
			}
			return (int) file.length();
		} catch (Exception _e) {
			return 0;
		}
	}

	public static long getFolderSize(String _path) {
		try {
			File file;
			file = new File(_path);
			if (file == null || !file.exists()) {
				return 0;
			}
			if (!file.isDirectory()) {
				return (int) file.length();
			}
			final List<File> dirs = new LinkedList<>();
			dirs.add(file);
			long result = 0;
			while (!dirs.isEmpty()) {
				final File dir = dirs.remove(0);
				if (!dir.exists()) {
					continue;
				}
				final File[] listFiles = dir.listFiles();
				if (listFiles == null || listFiles.length == 0) {
					continue;
				}
				for (final File child : listFiles) {
					result += child.length();
					if (child.isDirectory()) {
						dirs.add(child);
					}
				}
			}
			return result;
		} catch (Exception _e) {
			return 0;
		}
	}

	public static String getFilePathFromUri(Context context, Uri uri) {
		String filePath = null;
		if ("content".equalsIgnoreCase(uri.getScheme())) {
			String[] projection = {MediaStore.Files.FileColumns.DATA};
            try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
                    filePath = cursor.getString(index);
                }
            } catch (Exception e) {
				Log.e(TAG, "getFilePathFromUri: ", e);
            }
		} else if ("file".equalsIgnoreCase(uri.getScheme())) {
			filePath = uri.getPath();
		}
		return filePath;
	}

	public static boolean isValidFilePath(Activity activity, String filePath, boolean isShowDialog) {
		if (filePath == null || filePath.isEmpty()) {
			if (isShowDialog) {
				DialogUtils.oneDialog(activity,
						activity.getString(R.string.problem_has_been_detected),
						activity.getString(R.string.invalid_file_path_content),
						activity.getString(R.string.ok),
						true,
						R.drawable.folder_24px,
						true,
						null,
						null);
			}
			return false;
		}
		return true;
	}

	public static void openFolder(Context context, String folderPath) {
		File folder = new File(folderPath);

		if (!folder.exists() || !folder.isDirectory()) {
			DialogUtils.oneDialog(
					context,
					context.getString(R.string.oops),
					context.getString(R.string.directory_does_not_exist),
					context.getString(R.string.ok),
					true,
					R.drawable.error_96px,
					true,
					null,
					null
			);
			Log.e(TAG, "openFolder: Folder not found!");
			return;
		}

		Uri uri = FileProvider.getUriForFile(
				context,
				context.getPackageName() + ".provider",
				folder
		);

		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(uri, "resource/folder");
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		try {
			context.startActivity(intent);
		} catch (Exception e) {
			DialogUtils.oneDialog(
					context,
					context.getString(R.string.oops),
					context.getString(R.string.there_is_no_app_to_perform_this_action),
					context.getString(R.string.ok),
					true,
					R.drawable.error_96px,
					true,
					null,
					null
			);
			Log.e(TAG, "openFolder: " + e.getMessage());
		}
	}

	/**
	 * Maximum file size for CRC32C calculation (100 MB).
	 * Larger files will be skipped to avoid memory/performance issues.
	 */
	public static final long MAX_CRC_FILE_SIZE = 100L * 1024 * 1024;

	/**
	 * Calculates CRC32C checksum for a file.
	 * Uses the Castagnoli polynomial for better error detection.
	 * 
	 * @param file The file to calculate checksum for
	 * @return CRC32C checksum value
	 * @throws IOException If file cannot be read or is too large
	 * @throws IllegalArgumentException If file is null, doesn't exist, or is a directory
	 */
	public static long calculateCRC32C(File file) throws IOException {
		if (file == null) {
			throw new IllegalArgumentException("File cannot be null");
		}
		if (!file.exists()) {
			throw new IllegalArgumentException("File does not exist: " + file.getPath());
		}
		if (!file.isFile()) {
			throw new IllegalArgumentException("Path is not a file: " + file.getPath());
		}
		if (file.length() > MAX_CRC_FILE_SIZE) {
			throw new IOException("File too large for checksum calculation (max 100MB)");
		}
		
		java.util.zip.CRC32C crc = new java.util.zip.CRC32C();
		try (FileInputStream fis = new FileInputStream(file)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = fis.read(buffer)) != -1) {
				crc.update(buffer, 0, read);
			}
		}
		return crc.getValue();
	}

	/**
	 * Formats a CRC32C checksum as an 8-character hex string.
	 * 
	 * @param crc The CRC32C value (must be non-negative)
	 * @return Formatted hex string (e.g., "A1B2C3D4")
	 */
	public static String formatCRC32C(long crc) {
		return String.format("%08X", crc & 0xFFFFFFFFL);
	}

	/**
	 * Calculates and formats CRC32C checksum for a file.
	 * Returns descriptive error message if calculation fails.
	 * 
	 * @param file The file to calculate checksum for
	 * @return Formatted checksum or descriptive error message
	 */
	public static String getFileCRC32CString(File file) {
		if (file == null) {
			return "Error: File is null";
		}
		if (!file.exists()) {
			return "Error: File not found";
		}
		if (!file.isFile()) {
			return "Error: Not a file";
		}
		if (file.length() > MAX_CRC_FILE_SIZE) {
			return "Skipped: File too large (>100MB)";
		}
		try {
			long crc = calculateCRC32C(file);
			return formatCRC32C(crc);
		} catch (IOException e) {
			Log.e(TAG, "getFileCRC32CString: ", e);
			return "Error: " + e.getMessage();
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "getFileCRC32CString: ", e);
			return "Error: " + e.getMessage();
		}
	}
}
