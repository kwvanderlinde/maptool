/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * MapTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <http://www.gnu.org/licenses/> and specifically the Affero license
 * text at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.lib;

import com.thoughtworks.xstream.XStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import net.rptools.maptool.client.ui.token.BarTokenOverlay;
import net.rptools.maptool.model.AStarCellPointConverter;
import net.rptools.maptool.model.ShapeType;
import net.rptools.maptool.model.converters.WallTopologyConverter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileUtil {
  private static final Logger log = LogManager.getLogger(FileUtil.class);

  /**
   * Reads the entire content of the given file into a byte array.
   *
   * @deprecated use {@link FileUtils#readFileToByteArray(File)} instead.
   * @param file the file
   * @return byte contents of the file
   * @throws IOException in case of an I/O error
   */
  @Deprecated
  public static byte[] loadFile(File file) throws IOException {
    return FileUtils.readFileToByteArray(file);
  }

  /**
   * Reads the entire content of the given file into a byte array.
   *
   * @deprecated use {@link FileUtils#readFileToByteArray(File)} instead.
   * @param file the file
   * @return byte contents of the file
   * @throws IOException in case of an I/O error
   */
  @Deprecated
  public static byte[] getBytes(File file) throws IOException {
    return FileUtils.readFileToByteArray(file);
  }

  public static Object objFromResource(String res) throws IOException {
    XStream xs = getConfiguredXStream();
    try (InputStream is = FileUtil.class.getClassLoader().getResourceAsStream(res)) {
      return xs.fromXML(new InputStreamReader(is, StandardCharsets.UTF_8));
    }
  }

  public static byte[] loadResource(String resource) throws IOException {
    try (InputStream is = FileUtil.class.getClassLoader().getResourceAsStream(resource)) {
      if (is == null) {
        throw new IOException("Resource \"" + resource + "\" cannot be opened as stream.");
      }
      return IOUtils.toByteArray(
          new InputStreamReader(is, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
  }

  public static List<String> getLines(File file) throws IOException {
    try (FileReader fr = new FileReader(file)) {
      return IOUtils.readLines(fr);
    }
  }

  public static void saveResource(String resource, File destDir) throws IOException {
    int index = resource.lastIndexOf('/');
    String filename = index >= 0 ? resource.substring(index + 1) : resource;
    saveResource(resource, destDir, filename);
  }

  public static void saveResource(String resource, File destDir, String filename)
      throws IOException {
    File outFilename = new File(destDir, filename);
    try (InputStream inStream = FileUtil.class.getResourceAsStream(resource);
        OutputStream outStream = new BufferedOutputStream(new FileOutputStream(outFilename))) {
      IOUtils.copy(inStream, outStream);
    }
  }

  private static final Pattern TRIM_EXTENSION_PATTERN = Pattern.compile("^(.*)\\.([^\\.]*)$");

  /**
   * Returns the file with its name modified to add the extension if it doesn't have already.
   *
   * @param file the file that might need the extension
   * @param extension the extension to add, if it is missing
   * @return the file with the correct extension
   */
  public static File getFileWithExtension(File file, String extension) {
    if (file.getName().endsWith(extension)) {
      return file;
    } else {
      return new File(file.getAbsolutePath() + extension);
    }
  }

  public static String getNameWithoutExtension(File file) {
    return getNameWithoutExtension(file.getName());
  }

  public static String getNameWithoutExtension(String filename) {
    if (filename == null) {
      return null;
    }
    Matcher matcher = TRIM_EXTENSION_PATTERN.matcher(filename);
    if (!matcher.matches()) {
      return filename;
    }
    return matcher.group(1);
  }

  public static String getNameWithoutExtension(URL url) {
    String file = url.getFile();
    try {
      file = url.toURI().getPath();
      // int beginning = file.lastIndexOf(File.separatorChar); // Don't need to strip the path since
      // the File()
      // constructor will take care of that
      // file = file.substring(beginning < 0 ? 0 : beginning + 1);
    } catch (URISyntaxException e) {
      // If the conversion doesn't work, ignore it and use the original file name.
    }
    return getNameWithoutExtension(new File(file));
  }

  public static byte[] getBytes(URL url) throws IOException {
    try (InputStream is = url.openStream()) {
      return IOUtils.toByteArray(is);
    }
  }

  /**
   * Returns the data in a file using the UTF-8 character encoding. The platform default may not be
   * appropriate since the file could've been produced on a different platform. The only safe thing
   * to do is use UTF-8 and hope that everyone uses it by default when they edit text files. :-/
   *
   * @param is stream to get string from
   * @deprecated This is not in use, and {@link IOUtils#toCharArray(InputStream, String)} should be
   *     used directly anyways
   * @return the requested String
   * @throws IOException in case of an I/O error
   */
  @Deprecated
  public static String getString(InputStream is) throws IOException {
    if (is == null) throw new IllegalArgumentException("InputStream cannot be null");
    return IOUtils.toString(is, StandardCharsets.UTF_8);
  }

  /**
   * Reads the given file as UTF-8 bytes and returns the contents as a standard Java string.
   *
   * @deprecated This is not in use, and {@link FileUtils#readFileToString(File, String)} should be
   *     used directly anyways
   * @param file file to retrieve contents from
   * @return String representing the contents
   * @throws IOException in case of an I/O error
   */
  @Deprecated
  public static String getString(File file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("file cannot be null");
    }
    return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
  }

  /**
   * Given an InputStream this method tries to figure out what the content type might be.
   *
   * @param in the InputStream to check
   * @return a <code>String</code> representing the content type name
   */
  public static String getContentType(InputStream in) {
    String type = "";
    try {
      type = URLConnection.guessContentTypeFromStream(in);
      log.debug("result from guessContentTypeFromStream() is {}", type);
    } catch (IOException e) {
    }
    return type;
  }

  /**
   * Given a URL this method tries to figure out what the content type might be based only on the
   * filename extension.
   *
   * @param url the URL to check
   * @return a <code>String</code> representing the content type name
   */
  public static String getContentType(URL url) {
    String type = "";
    type = URLConnection.guessContentTypeFromName(url.getPath());
    log.debug("result from guessContentTypeFromName({}) is {}", url.getPath(), type);
    return type;
  }

  /**
   * Given a <code>File</code> this method tries to figure out what the content type might be based
   * only on the filename extension.
   *
   * @param file the File to check
   * @return a <code>String</code> representing the content type name
   */
  public static String getContentType(File file) {
    try {
      return getContentType(file.toURI().toURL());
    } catch (MalformedURLException e) {
      return null;
    }
  }

  /**
   * Returns a {@link BufferedReader} from the given {@code File} object. The contents of the file
   * are expected to be UTF-8.
   *
   * @param file the input data source
   * @return a String representing the data
   * @throws IOException in case of an I/O error
   */
  public static BufferedReader getFileAsReader(File file) throws IOException {
    return new BufferedReader(
        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
  }

  public static InputStream getFileAsInputStream(File file) throws IOException {
    return getURLAsInputStream(file.toURI().toURL());
  }

  /**
   * Given a URL this method determines the content type of the URL (if possible) and then returns
   * an InputStream.
   *
   * @param url the source of the data stream
   * @return InputStream representing the data
   * @throws IOException in case of an I/O error
   */
  public static InputStream getURLAsInputStream(URL url) throws IOException {
    InputStream is = null;
    URLConnection conn = null;
    // We're assuming character here, but it could be bytes. Perhaps we should
    // check the MIME type returned by the network server?
    conn = url.openConnection();
    if (log.isDebugEnabled()) {
      String type = URLConnection.guessContentTypeFromName(url.getPath());
      log.debug("result from guessContentTypeFromName(" + url.getPath() + ") is " + type);
      type = getContentType(conn.getInputStream());
      log.debug("result from getContentType(" + url.getPath() + ") is " + type);
    }
    is = conn.getInputStream();
    return is;
  }

  /**
   * Writes given bytes to file indicated by <code>file</code>. This method will overwrite any
   * existing file at that location, and will create any sub-directories required.
   *
   * @deprecated use {@link FileUtils#writeByteArrayToFile(File, byte[])} instead.
   * @param file file to store data in
   * @param data the data to store in file
   * @throws IOException in case of an I/O error
   */
  @Deprecated
  public static void writeBytes(File file, byte[] data) throws IOException {
    FileUtils.writeByteArrayToFile(file, data);
  }

  /**
   * Copies <code>sourceFile</code> to <code>destFile</code> overwriting as required, and <b>not</b>
   * preserving the source file's last modified time. The destination directory is created if it
   * does not exist, and if the destination file exists, it is overwritten.
   *
   * @param sourceFile the source file
   * @param destFile the destination file
   * @throws IOException in case of an I/O error
   */
  public static void copyFile(File sourceFile, File destFile) throws IOException {
    FileUtils.copyFile(sourceFile, destFile, false);
  }

  /**
   * Unzips the indicated file from the <code>classpathFile</code> location into the indicated
   * <code>destDir</code>.
   *
   * @param classpathFile The resource name
   * @param destDir the destination directory
   * @throws IOException in case of an I/O error
   */
  public static void unzip(String classpathFile, File destDir) throws IOException {
    try {
      unzip(FileUtil.class.getClassLoader().getResource(classpathFile), destDir);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  /**
   * Loads the given {@link URL}, and unzips the URL's contents into the given <code>destDir</code>.
   *
   * @param url the url to load
   * @param destDir the destination directory to save the files in
   * @throws IOException in case of an I/O error
   */
  public static void unzip(URL url, File destDir) throws IOException {
    if (url == null) throw new IOException("URL cannot be null");

    InputStream is = url.openStream();
    try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
      unzip(zis, destDir);
    }
  }

  public static void unzip(ZipInputStream in, File destDir) throws IOException {
    if (in == null) throw new IOException("input stream cannot be null");

    // Prepare destination
    destDir.mkdirs();
    File absDestDir = destDir.getAbsoluteFile();

    // Pull out the files
    ZipEntry entry = null;
    while ((entry = in.getNextEntry()) != null) {
      if (entry.isDirectory()) continue;

      // Prepare file destination
      File entryFile = new File(absDestDir, entry.getName());
      entryFile.getParentFile().mkdirs();

      try (OutputStream out = new FileOutputStream(entryFile)) {
        IOUtils.copy(in, out);
      }
      in.closeEntry();
    }
  }

  public static void unzipFile(File sourceFile, File destDir) throws IOException {
    if (!sourceFile.exists()) throw new IOException("source file does not exist: " + sourceFile);

    try (ZipFile zipFile = new ZipFile(sourceFile)) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();

      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (entry.isDirectory()) continue;

        File file = new File(destDir, entry.getName());
        String path = file.getAbsolutePath();
        file.getParentFile().mkdirs();

        try (InputStream is = zipFile.getInputStream(entry);
            OutputStream os = new BufferedOutputStream(new FileOutputStream(path))) {
          IOUtils.copy(is, os);
        }
      }
    }
  }

  /**
   * Copies all bytes from InputStream to OutputStream without closing either stream.
   *
   * @deprecated not in use. Use {@link IOUtils#copy(InputStream, OutputStream)} instead.
   * @param is the InputStream to read from
   * @param os the OutputStream to write to
   * @throws IOException in case of an I/O error
   */
  @Deprecated
  public static void copyWithoutClose(InputStream is, OutputStream os) throws IOException {
    IOUtils.copy(is, os);
  }

  /**
   * Copies all bytes from InputStream to OutputStream, and close both streams before returning.
   *
   * @deprecated not in use. Use {@link IOUtils#copy(InputStream, OutputStream)} instead and
   *     try-with-resources
   * @param is input stream to read data from.
   * @param os output stream to write data to.
   * @throws IOException in case of an I/O error
   */
  @Deprecated
  public static void copyWithClose(InputStream is, OutputStream os) throws IOException {
    try {
      IOUtils.copy(is, os);
    } finally {
      IOUtils.closeQuietly(is);
      IOUtils.closeQuietly(os);
    }
  }

  /**
   * Recursively deletes all files and/or directories that have been modified longer than <code>
   * daysOld</code> days ago. Note that this will recursively examine a directory, and only deletes
   * those items that are too old.
   *
   * @param file the file or directory to recursively check and possibly delete
   * @param daysOld number of days old a file or directory can be before it is considered for
   *     deletion
   */
  public static void delete(File file, int daysOld) {
    Calendar olderThan = new GregorianCalendar();
    olderThan.add(Calendar.DATE, -daysOld);

    boolean shouldDelete = new Date(file.lastModified()).before(olderThan.getTime());

    if (file.isDirectory()) {
      // Wipe the contents first
      for (File currfile : file.listFiles()) {
        if (".".equals(currfile.getName()) || "..".equals(currfile.getName())) continue;
        delete(currfile, daysOld);
      }
    }
    if (shouldDelete) file.delete();
  }

  /**
   * Recursively deletes the given file or directory
   *
   * @see FileUtils#deleteQuietly(File)
   * @param file to recursively delete
   */
  public static void delete(File file) {
    FileUtils.deleteQuietly(file);
  }

  /**
   * Replace invalid File name characters, useful for token Save function to replace the : in Lib
   * tokens.
   *
   * @author Jamz
   * @since 1.4.0.2
   * @param fileName the name of the file
   * @return the fileName with invalid characters replaced by _
   */
  public static String stripInvalidCharacters(String fileName) {
    return fileName = fileName.replaceAll("[^\\w\\s.,-]", "_");
  }

  /**
   * Return an XStream which allows net.rptools.**, java.awt.**, sun.awt.** May be too permissive,
   * but it Works For Me(tm)
   *
   * @return a configured XStream
   */
  public static XStream getConfiguredXStream() {
    XStream xStream = new XStream();
    XStream.setupDefaultSecurity(xStream);
    xStream.allowTypesByWildcard(new String[] {"net.rptools.**", "java.awt.**", "sun.awt.**"});
    xStream.registerConverter(new AStarCellPointConverter());
    xStream.registerConverter(new WallTopologyConverter(xStream));
    xStream.addImmutableType(ShapeType.class, true);
    xStream.addImmutableType(BarTokenOverlay.Side.class, true);
    return xStream;
  }
}
