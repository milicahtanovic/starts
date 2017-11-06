/*                                                                                                                                                                                  
 * Copyright (c) 2015 - Present. The STARTS Team. All Rights Reserved.                                                                                                              
 */

package edu.illinois.starts.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import edu.illinois.starts.helpers.Writer;
import edu.illinois.starts.maven.AgentLoader;
import edu.illinois.starts.util.ChecksumUtil;

public class ClasspathUtil {

    private static final Class[] parameters = new Class[] { URL.class };

    public static boolean checkIfSameClassPath(String sfPathString, String oldSf) {
        String cleanSfPathString = cleanClassPath(sfPathString);
        String cleanOldSfPath = cleanClassPath(oldSf);
        if (cleanOldSfPath.equals(cleanSfPathString)) {
            return true;
        } else {
            Set<String> sfClassPathSet = new HashSet<>(Arrays.asList(cleanSfPathString.split(File.pathSeparator)));
            Set<String> oldSfClassPathSet = new HashSet<>(Arrays.asList(cleanOldSfPath.split(File.pathSeparator)));
            if (sfClassPathSet.equals(oldSfClassPathSet)) {
                return compareJarContents(sfPathString);
            }
        }
        return false;
    }

    private static boolean compareJarContents(String cleanSfClassPath) {
        List<String> listOfJars = Arrays.asList(cleanSfClassPath.split(File.pathSeparator));
        Map<String, Map<String, String>> jarToJarContents = new HashMap<>();
        URLClassLoader cl = new URLClassLoader(new URL[] {});
        ChecksumUtil checksum = new ChecksumUtil(true);
        ZipInputStream zip;
        try {
            for (String jar : listOfJars) {
                // load the jar
                addFile(jar, cl);
                jarToJarContents.put(jar, new HashMap<String, String>());
                zip = new ZipInputStream(new FileInputStream(Paths.get(jar).toString()));
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (entry.getName().endsWith(".class")) {
                        jarToJarContents.get(jar).put(entry.getName(),
                                                      checksum.getCheckSum(cl.getResource(
                                                      entry.getName())).toString().replace("jar:file:", ""));
                    }
                }
            }
                // compare methods of classes present in jars
            for (int i = 0; i < listOfJars.size(); i++) {
                List<String> currentClasses = new ArrayList<>();
                currentClasses.addAll(jarToJarContents.get(listOfJars.get(i)).keySet());
                for (int j = i; j < listOfJars.size(); j++) {
                    List<String> otherClasses = new ArrayList<>();
                    otherClasses.addAll(jarToJarContents.get(listOfJars.get(j)).keySet());
                    for (int k = 0; k < currentClasses.size(); k++) {
                        String currentClass = jarToJarContents.get(listOfJars.get(i)).get(currentClasses.get(k));
                        String[] currentClassChunks = currentClass.split("/");
                        for (int l = k; l < otherClasses.size(); l++) {
                            String otherClass = jarToJarContents.get(listOfJars.get(j)).get(currentClasses.get(l));
                            String[] otherClassChunks = otherClass.split("/");
                            if (currentClassChunks[currentClassChunks.length - 1].equals(
                                                   otherClassChunks[otherClassChunks.length - 1])) {
                                URL curentUrl = new URL(currentClass);
                                URL otherUrl = new URL(otherClass);
                                if (checksum.getCheckSum(curentUrl).equals(checksum.getCheckSum(otherUrl))) {
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
        } catch (FileNotFoundException fnoe) {
            fnoe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return true;
    }

    public static boolean checkIfSameJarChecksums(String sfClassPath, String oldChecksumPathFileName) {
        Map<String, String> checksumMap = new HashMap<>();
        boolean noException = true;
        if (!new File(oldChecksumPathFileName).exists()) {
            return false;
        }
        try (BufferedReader fileReader = new BufferedReader(new FileReader(oldChecksumPathFileName))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                String[] elems = line.split(",");
                checksumMap.put(elems[0], elems[1]);
            }
            String cleanSfClassPath = cleanSfClassPath(sfClassPath);
            String[] jars = cleanSfClassPath.split(File.pathSeparator);
            for (int i = 0; i < jars.length; i++) {
                String[] elems = Writer.getJarToChecksumMapping(jars[i]).split(",");
                String oldCS = checksumMap.get(elems[0]);
                if (!elems[1].equals(oldCS)) {
                    return false;
                }
            }
        } catch (IOException ioe) {
            noException = false;
            ioe.printStackTrace();
        }
        return noException;
    }

    public static String readinSfFile(String oldSfPathFileName) {
        String oldSfClasspath = "";
        if (!new File(oldSfPathFileName).exists()) {
            return oldSfClasspath;
        }
        try {
            oldSfClasspath = Files.readAllLines(Paths.get(oldSfPathFileName)).get(0);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return oldSfClasspath;
    }
    
    private static String cleanClassPath(String cp) {
        String[] paths = cp.split(File.pathSeparator);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.length; i++) {
            if (paths[i].contains(File.separator + "target" +  File.separator + "classes")
                || paths[i].contains(File.separator + "target" + File.separator + "test-classes")
                || paths[i].contains("-SNAPSHOT.jar")) {
                continue;
            }
            if (sb.length() == 0) {
                sb.append(paths[i]);
            } else {
                sb.append(File.pathSeparator);
                sb.append(paths[i]);
            }
        }
        return sb.toString();
    }

    private static void addFile(String string, URLClassLoader sysloader) throws IOException {
        File file = new File(string);
        addFile(file, sysloader);
    }

    private static void addFile(File file, URLClassLoader sysloader) throws IOException {
        addURL(file.toURI().toURL(), sysloader);
    }

    private static void addURL(URL url, URLClassLoader sysloader) throws IOException {
        try {
            Class sysclass = URLClassLoader.class;
            Method method = sysclass.getDeclaredMethod("addURL", parameters);
            method.setAccessible(true);
            method.invoke(sysloader, new Object[] { url });
        } catch (Throwable th) {
            th.printStackTrace();
            throw new IOException("Error, could not add URL to system classloader");
        }
    }

}

