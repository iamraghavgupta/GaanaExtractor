package in.pathri.gaanaextractor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;

import com.jayway.jsonpath.JsonPath;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

public class MainExtractor {
	static final Logger logger = LogManager.getLogger();
	static List<String> data404 = new ArrayList<String>();
	static List<String> fileReadError = new ArrayList<String>();
	static List<String> fileWriteError = new ArrayList<String>();
	static Map<String, ArrayList<String>> fileTagError = new HashMap<String,ArrayList<String>>();
	static String srcDir = "";

	public static void main(String[] args) {
		System.out.println(System.lineSeparator() + "**********Gaana Extractor**********");
		logger.entry(args);
		String songsDir = "";
		boolean toAlbumFolder = true;
		if(args.length == 0){
			songsDir = System.getProperty("user.dir");
			logger.info("**NOTE**:Using the current location of JAR as the location of songs. To override please start with the song location as an argument to the JAR");
			//System.out.println("Using the current location of JAR as the location of songs. To override please start with the song location as an argument to the JAR");
		}else if(args.length == 1){
			songsDir = args[0];
			logger.info("**NOTE**:Converted songs will be grouped into folders based on their Albums. To disable please supply 'false' as a second argument to the JAR");
			//System.out.println("Converted songs will be grouped into folders based on their Albums. To disable please supply 'false' as a second argument to the JAR");
		}else{
			songsDir = args[0];
			toAlbumFolder = Boolean.valueOf(args[1]);
		}
		
		songsDir = songsDir.replace("\\", "/");
		srcDir = songsDir.replace("/", "\\");
		logger.info("**NOTE**:Songs Source Directory::{}",songsDir);
		if(toAlbumFolder){
			logger.info("**NOTE**:Songs will be grouped into Folders based on Album names");
		}else{
			logger.info("**NOTE**:Songs will be converted as is");
		}
		
//		System.out.println("Songs Source Directory::" + songsDir);
//		System.out.println(toAlbumFolder?"Songs will be grouped into Folders based on Album names":"Songs will be converted as is");
		logger.info(System.lineSeparator());
		extract(songsDir, toAlbumFolder);

		logger.info("{}",() -> prepareFinalStats());
				
//		System.out.println("File Data Not Found : " + data404.stream().collect(Collectors.joining(";")));
//		System.out.println("File Read Error : " + fileReadError.stream().collect(Collectors.joining(";")));
//		System.out.println("File Write Error : " + fileWriteError.stream().collect(Collectors.joining(";")));
		
//		System.out.println("File Write Error : ");
//		for(Entry<String, ArrayList<String>> entry : fileTagError.entrySet()){
//			System.out.println("File Name : " + entry.getKey());
//			System.out.println("Tags : " + entry.getValue().stream().collect(Collectors.joining(";")));
//		}
			
		logger.traceExit();
	}
	


	private static void extract(String srcPath, boolean toAlbumFolder) {
		logger.entry(srcPath, toAlbumFolder);
		String trgtFolder = "converted";
		String trgtPath = srcPath + ((srcPath.endsWith("/") | srcPath.endsWith("\\")) ? "" : "/") + trgtFolder;
		Map<Integer, Path> fileIds = new HashMap<Integer, Path>();
		fileIds = getFileIds(srcPath);
		JSONObject songMeta = getSongsDetail(fileIds);
		if (songMeta != null) {
			copyConvert(fileIds, songMeta, trgtPath, toAlbumFolder);
		}

		logger.traceExit();
	}

	private static void copyConvert(Map<Integer, Path> fileIds, JSONObject songMeta, String trgtPath,
			boolean toAlbumFolder) {
		logger.entry(fileIds, songMeta, trgtPath, toAlbumFolder);
		for (Entry<Integer, Path> fileEntry : fileIds.entrySet()) {
			String fileName = fileEntry.getKey().toString();
			ArrayList<String> tagErrors = new ArrayList<String>();			
			File srcFile = fileEntry.getValue().toFile();
			logger.info("Converting:: {}",srcFile.getAbsolutePath().replace(srcDir, ""));
//			System.out.println("Converting::" + srcFile.getAbsolutePath());
			try {
				AudioFile f;
				f = AudioFileIO.readMagic(srcFile);
				Tag tag = f.getTag();

				String strBasePath = "$.tracks[?(@.track_id==" + fileName + ")].";

				JSONArray arrFileName = (JSONArray) JsonPath.read(songMeta, strBasePath + "track_title");
				if (!arrFileName.isEmpty()) {
					String strFileName = (String) (arrFileName).get(0);
					strFileName = FileNameCleaner.cleanFileName(strFileName);

					String trgFolderPath = trgtPath;

					if (toAlbumFolder) {
						String strFolderName = (String) ((JSONArray) JsonPath.read(songMeta,
								strBasePath + "album_title")).get(0);
						strFolderName = FileNameCleaner.cleanFileName(strFolderName);

						trgFolderPath = trgFolderPath + "/" + strFolderName;
					}
					File trgtFolder = new File(trgFolderPath);
					if (!trgtFolder.exists()) {
						trgtFolder.mkdirs();
					}

					String trgtFilePath = trgtFolder.getAbsolutePath() + "/" + strFileName;
					String strData = "";
					try {
						strData = (String) ((JSONArray) JsonPath.read(songMeta, strBasePath + "album_title")).get(0);
						tag.setField(FieldKey.ALBUM, strData);
					} catch (KeyNotFoundException | FieldDataInvalidException e) {
						tagErrors.add("ALBUM");
						logger.catching(e);
//						e.printStackTrace();
					}

					try {
						strData = (String) ((JSONArray) JsonPath.read(songMeta, strBasePath + "track_title")).get(0);
						tag.setField(FieldKey.TITLE, strData);
					} catch (KeyNotFoundException | FieldDataInvalidException e) {
						tagErrors.add("TITLE");
						logger.catching(e);
//						e.printStackTrace();
					}

					try {
						strData = (String) ((JSONArray) JsonPath.read(songMeta, strBasePath + "release_date")).get(0);
						tag.setField(FieldKey.YEAR, strData);
					} catch (KeyNotFoundException | FieldDataInvalidException e) {
						tagErrors.add("YEAR");
						logger.catching(e);
//						e.printStackTrace();
					}
                                        
                                        try {
						strData = (String) ((JSONArray) JsonPath.read(songMeta, strBasePath + "lyrics_url")).get(0);
						tag.setField(FieldKey.URL_LYRICS_SITE, strData);
					} catch (KeyNotFoundException | FieldDataInvalidException e) {
						tagErrors.add("LYRICS_URL");
						logger.catching(e);
//						e.printStackTrace();
					}

					try {
						strData = (String) ((JSONArray) JsonPath.read(songMeta, strBasePath + "artwork_large")).get(0);
						byte[] strImgData = getByteArray(strData);
						Artwork artwork = ArtworkFactory.getNew();
						artwork.setBinaryData(strImgData);
						tag.setField(artwork);
					} catch (KeyNotFoundException | FieldDataInvalidException | UnsupportedOperationException e) {
						tagErrors.add("ART_WORK");
						logger.catching(e);
//						e.printStackTrace();
					}
					if(!tagErrors.isEmpty()){
						fileTagError.put(fileName, tagErrors);
					}

					AudioFileIO.writeAs(f, trgtFilePath);
				} else {
					data404.add(fileName);
				}

			} catch (CannotReadException | IOException | TagException | ReadOnlyFileException
					| InvalidAudioFrameException e) {
				fileReadError.add(fileName);
				logger.catching(e);
//				e.printStackTrace();
			} catch (CannotWriteException e) {
				fileWriteError.add(fileName);
				logger.catching(e);
//				e.printStackTrace();
			}

		}
		logger.traceExit();
	}

	private static JSONObject getSongsDetail(Map<Integer, Path> fileIds) {
		logger.entry(fileIds);
		String endPoint = "http://api.gaana.com/";
		JSONObject songsDetail = new JSONObject();
		JSONObject songsDetailFull = new JSONObject();
		JSONArray tracks = new JSONArray();
		JSONArray tracksFull = new JSONArray();
		Map<String, String> params = new HashMap<String, String>();
		params.put("type", "song");
		params.put("subtype", "song_detail");
		
		if (!fileIds.isEmpty()) {
			int i = 0;
			String strFileIds = "";
			for (Integer fileId : fileIds.keySet()) {
				i = i + 1;
				if (strFileIds == "") {
					strFileIds = fileId.toString();
				} else {
					strFileIds = strFileIds + "," + fileId.toString();
				}
				if (i == 10) {
					params.put("track_id", strFileIds);
					songsDetail = getSongsDetail(endPoint, params);
					tracks = (JSONArray) songsDetail.get("tracks");
					if (tracks != null && !tracks.isEmpty()) {
						tracksFull.addAll(tracks);
					}
					i = 0;
					strFileIds = "";
				}
			}
			if (!strFileIds.isEmpty()) {
				params.put("track_id", strFileIds);
				songsDetail = getSongsDetail(endPoint, params);
				tracks = (JSONArray) songsDetail.get("tracks");
				if (tracks != null && !tracks.isEmpty()) {
					tracksFull.addAll(tracks);
				}
				i = 0;
				strFileIds = "";
			}

			songsDetailFull.put("tracks", tracksFull);
			logger.traceExit(songsDetailFull);
			return songsDetailFull;
		}
		logger.traceExit();
		return null;
	}

	public static JSONObject getSongsDetail(String endPoint, Map<String, String> params) {
		logger.entry(endPoint, params);
		try {
			String songMeta = HTTPHelper.sendGet(endPoint, params);
			logger.debug("SongMeta::{}",songMeta);
//			System.out.println(songMeta);
			logger.traceExit(songMeta);
			return (JSONObject) JSONValue.parse(songMeta);
		} catch (Exception e) {
//			e.printStackTrace();
			logger.catching(e);
			logger.traceExit(null);
			return null;
		}
	}

	public static Map<Integer, Path> getFileIds(String path) {
		logger.entry(path);
		final Map<Integer, Path> fileIds = new HashMap<Integer, Path>();
		Path p = Paths.get(path);
		FileVisitor<Path> fv = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				logger.entry(file,attrs);
				if (file.getFileName().toString().matches("\\d+")) {
					Integer id = Integer.parseInt(file.getFileName().toString());
					Path path = file;
					fileIds.put(id, path);
				} else {
					//Ignoring Non numeric files
				}
				logger.traceExit(FileVisitResult.CONTINUE);
				return FileVisitResult.CONTINUE;
			}
		};

		try {
			Files.walkFileTree(p, fv);
		} catch (IOException e) {
//			e.printStackTrace();
			logger.catching(e);
		}
		logger.traceExit(fileIds);
		return fileIds;

	}

	
// Utility Methods	
	private static byte[] getByteArray(String strURL) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		InputStream is = null;
		try {
			URL url = new URL(strURL);
			is = url.openStream();
			byte[] byteChunk = new byte[4096];
			int n;

			while ((n = is.read(byteChunk)) > 0) {
				baos.write(byteChunk, 0, n);
			}
			return (baos.toByteArray());
		} catch (IOException e) {
			logger.catching(e);
//			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					logger.catching(e);
//					e.printStackTrace();
				}
			}
		}
		return null;
	}
	
	private static String prepareFinalStats(){
		String statValue = "";
		final String NONE_VALUE= "NONE";
		StringBuilder finalStats = new StringBuilder();		
		
		finalStats.append("File Data Not Found :: ");
		statValue = data404.stream().collect(Collectors.joining(";"));
		statValue = statValue.isEmpty()?NONE_VALUE:statValue;
		finalStats.append(statValue);
		
		finalStats.append(System.lineSeparator());
		
		finalStats.append("File Read Error :: ");
		statValue = fileReadError.stream().collect(Collectors.joining(";"));
		statValue = statValue.isEmpty()?NONE_VALUE:statValue;
		finalStats.append(statValue);
		
		finalStats.append(System.lineSeparator());
		
		finalStats.append("File Write Error :: ");
		statValue = fileWriteError.stream().collect(Collectors.joining(";"));
		statValue = statValue.isEmpty()?NONE_VALUE:statValue;
		finalStats.append(statValue);
		
		finalStats.append(System.lineSeparator());
		
		finalStats.append("File Tag Error ::");
		if(fileTagError.isEmpty()){
			finalStats.append(NONE_VALUE);
		}else{
			finalStats.append(System.lineSeparator());
			for(Entry<String, ArrayList<String>> entry : fileTagError.entrySet()){
				finalStats.append("\tFile Name :: ");
				finalStats.append(entry.getKey());
				finalStats.append("Tags :: ");
				finalStats.append(entry.getValue().stream().collect(Collectors.joining(";")));
				finalStats.append(System.lineSeparator());
			}			
		}
		

		
		return finalStats.toString();		
	}	
}
