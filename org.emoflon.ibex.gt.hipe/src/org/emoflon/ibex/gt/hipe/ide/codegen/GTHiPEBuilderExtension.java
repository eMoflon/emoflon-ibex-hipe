package org.emoflon.ibex.gt.hipe.ide.codegen;

import org.emoflon.ibex.gt.editor.ui.builder.GTBuilderExtension;
import org.emoflon.ibex.gt.hipe.runtime.IBeXToHiPEPatternTransformation;
import org.moflon.core.plugins.manifest.ManifestFileUpdater;
import org.moflon.core.utilities.EcoreUtils;

import IBeXLanguage.IBeXLanguagePackage;
import IBeXLanguage.IBeXPatternSet;
import hipe.generator.HiPEGenerator;
import hipe.network.HiPENetwork;
import hipe.pattern.HiPEPatternContainer;
import hipe.searchplan.simple.SimpleSearchPlan;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.jar.Manifest;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

public class GTHiPEBuilderExtension implements GTBuilderExtension{

	public static final String BUILDER_ID = "org.emoflon.ibex.gt.editor.ui.hipe.builder";
	
	private String packageName;
	private String packagePath;
	
	@Override
	public void run(IProject project) {
		//Logger.getRootLogger().info("HiPE plugin extension test 1: " + project.getProjectRelativePath().toString());
	}

	@Override
	public void run(IProject project, IPath packagePath) {
		Logger.getRootLogger().info("## HiPE ## Generating HiPE-Engine code..");
		double tic = System.currentTimeMillis();
		
		packageName = packagePath.toString().replace("/", ".");
		
		if(project.getFullPath().makeAbsolute().toPortableString().equals(packagePath.makeAbsolute().toPortableString())) {
			this.packagePath = packagePath.makeAbsolute().toPortableString();
		}else {
			this.packagePath = project.getFullPath().makeAbsolute().toPortableString();
		}
		
		log("Loading IBeX patterns..");
		String patternPath = this.packagePath+"//src-gen//" + packageName + "//api//ibex-patterns.xmi";
		IBeXPatternSet ibexPatterns = loadIBeXPatterns(patternPath);
		if(ibexPatterns == null)
			return;
		
		IFile file = project.getFile(patternPath);
		this.packagePath = file.getLocation().uptoSegment(file.getLocation().segmentCount()-5).makeAbsolute().toPortableString();
		
		log("Cleaning old code..");
		cleanOldCode();
		
		log("Creating jar directory..");
		createNewDirectory(this.packagePath+"/jars");
		File jarsDir1 = findJarsDirectory();
		File jarsDir2 = new File(this.packagePath+"/jars");
		
		log("Copying jars..");
		copyDirectoryContents(jarsDir1, jarsDir2);
		
		log("Updating Manifest & build properties..");
		updateManifest(this.packagePath, project);
		updateBuildProperties(this.packagePath);
		
		log("Converting IBeX to HiPE Patterns..");
		IBeXToHiPEPatternTransformation transformation = new IBeXToHiPEPatternTransformation();
		HiPEPatternContainer container = transformation.transform(ibexPatterns);
		
		log("Creating search plan & generating Rete network..");
		SimpleSearchPlan searchPlan = new SimpleSearchPlan(container);
		searchPlan.generateSearchPlan();
		HiPENetwork network = searchPlan.getNetwork();
		
		log("Generating Code..");
		HiPEGenerator.generateCode(packageName+".", this.packagePath, network);
		
		double toc = System.currentTimeMillis();
		Logger.getRootLogger().info("Code generation completed in "+ (toc-tic)/1000.0 + " seconds.");	
		
		
		log("## HiPE ## --> HiPE build complete!");
	}
	
	private void updateManifest(String packagePath, IProject project) {
		try {
			IFile manifest = ManifestFileUpdater.getManifestFile(project);
			File rawManifest = new File(this.packagePath+"/"+manifest.getFullPath().removeFirstSegments(1).toPortableString());
			BufferedReader br = new BufferedReader(new InputStreamReader(manifest.getContents()));
			StringBuilder sb = new StringBuilder();
			
			String line = br.readLine();
			boolean bcpFound = false;
			boolean jarsFound = false;
			boolean jarWritten = false;
			while(line != null) {
				
				if(line.contains("Bundle-ClassPath:")) {
					bcpFound = true;
				}
				if(line.contains("jars/,")) {
					jarsFound = true;
				}
				if(line.contains("jars/,") && jarWritten) {
					continue;
				}
				
				sb.append(line+"\n");
				
				if(bcpFound && !jarsFound && !jarWritten) {
					sb.append(" jars/,\n .\n");
					jarWritten = true;
				}
				
				
				line = br.readLine();
			}
			if(!bcpFound) {
				jarWritten = true;
				sb.append("Bundle-ClassPath: jars/,\n .\n");
			}
			br.close();
			
			if(!jarWritten) {
				log("--> Manifest already up to date.");
				return;
			}
			InputStream is = new ByteArrayInputStream(sb.toString().getBytes());
			OutputStream os = new FileOutputStream(rawManifest);
	        byte[] buffer = new byte[1024];
	        int length;
	        while ((length = is.read(buffer)) > 0) {
	            os.write(buffer, 0, length);
	        }
	        os.close();
	        is.close();
			
			
		} catch (CoreException | IOException e) {
			log("ERROR: Failed to update MANIFEST.MF.");
		}
	}
	
	private void updateBuildProperties(String packagePath) {
		File buildProps = new File(this.packagePath+"/build.properties");
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(buildProps)));
			StringBuilder sb = new StringBuilder();
			
			String line = br.readLine();
			boolean extraFound = false;
			boolean jarsFound = false;
			boolean jarsWritten = false;
			while(line != null) {
				
				if(line.contains("jars.extra.classpath")) {
					extraFound = true;
				}
				if(line.contains("jars/akka-actor_2.12-2.5.19.jar")) {
					jarsFound = true;
				}
				if(line.contains("jars/akka-actor_2.12-2.5.19.jar") && jarsWritten) {
					continue;
				}
				
				sb.append(line+"\n");
				
				if(extraFound && !jarsFound && !jarsWritten) {
					sb.append("                       jars/akka-actor_2.12-2.5.19.jar,\\\n");
					sb.append("                       jars/config-1.3.3.jar,\\\n");
					sb.append("                       jars/scala-java8-compat_2.12-0.8.0.jar,\\\n");
					sb.append("                       jars/scala-library-2.12.8.jar\n");
					jarsWritten = true;
				}
				
				line = br.readLine();
			}
			
			if(!extraFound) {
				jarsWritten = true;
				sb.append("jars.extra.classpath = jars/akka-actor_2.12-2.5.19.jar,\\\n");
				sb.append("                       jars/config-1.3.3.jar,\\\n");
				sb.append("                       jars/scala-java8-compat_2.12-0.8.0.jar,\\\n");
				sb.append("                       jars/scala-library-2.12.8.jar\n");
			}
			br.close();
			
			if(!jarsWritten) {
				log("--> build.properties already up to date.");
				return;
			}
			
			InputStream is = new ByteArrayInputStream(sb.toString().getBytes());
			OutputStream os = new FileOutputStream(buildProps);
	        byte[] buffer = new byte[1024];
	        int length;
	        while ((length = is.read(buffer)) > 0) {
	            os.write(buffer, 0, length);
	        }
	        os.close();
	        is.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static boolean deleteDirectory(File dir) {
		File[] contents  = dir.listFiles();
		if(contents != null) {
			for(File file : contents) {
				deleteDirectory(file);
			}
		}
		return dir.delete();
	}
	
	private static void createNewDirectory(String path) {
		File dir = new File(path);
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				log("ERROR: Directory in: "+path+" could not be created!");
			}else {
				log("--> Directory in: "+path+" created!");
			}
		}else {
			log("--> Directory already present in: "+path+", nothing to do.");
		}
	}
	
	private static void copyDirectoryContents(File dir1, File dir2) {
		List<File> contents = Arrays.asList(dir1.listFiles());
		contents.parallelStream().forEach(content -> {
			try {
				InputStream is = new FileInputStream(content);
		        File dest = new File(dir2.getAbsolutePath()+"/"+content.getName());
		        
		        if(!dest.exists()) {
		        	OutputStream os = new FileOutputStream(dest);
			        byte[] buffer = new byte[1024];
			        int length;
			        while ((length = is.read(buffer)) > 0) {
			            os.write(buffer, 0, length);
			        }
			        os.close();
		        }
		        is.close();
		    } catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
		});
	}
	
	private void cleanOldCode() {
		File dir = new File(this.packagePath+"/src-gen/" + packageName + "/hipe");
		if(dir.exists()) {
			log("--> Cleaning old source files in root folder: "+this.packagePath+"/src-gen/" + packageName + "/hipe");
			if(!deleteDirectory(dir)) {
				log("ERROR: Folder couldn't be deleted!");
			}
		} else {
			log("--> No previously generated code found, nothing to do!");
		}
	}
	
	@SuppressWarnings("null")
	private File findJarsDirectory() {
		File currentClass = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
		
		Path jarPath = currentClass.toPath();
		while(jarPath != null || !jarPath.toFile().getName().equals("jars")) {
			File current = jarPath.toFile();
			if(current.isDirectory()) {
				File[] contents = current.listFiles();
				for(File content : contents) {
					if(!content.isDirectory())
						continue;
					
					if(content.getName().equals("jars")) {
						jarPath = content.toPath();
						return jarPath.toFile();
					}
				}
				jarPath = jarPath.getParent();
			}else {
				jarPath = jarPath.getParent();
			}
		}
		return null;
	}
	
	private static void log(String lg) {
		Logger.getRootLogger().info(lg);
	}
	
	private static IBeXPatternSet loadIBeXPatterns(String path) {
		Resource res = null;
		try {
			res = loadResource(path);
		} catch (Exception e) {
			log("ERROR: Couldn't load ibex pattern set: \n" + e.getMessage());
			e.printStackTrace();
		}
		
		if(res == null) {
			return null;
		}
		return (IBeXPatternSet)res.getContents().get(0);
	}
	
	private static Resource loadResource(String path) throws Exception {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("ibex-patterns-for-hipe", new XMIResourceFactoryImpl());
		ResourceSet rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
		rs.getPackageRegistry().put(IBeXLanguagePackage.eNS_URI, IBeXLanguagePackage.eINSTANCE);
		
		URI uri = URI.createFileURI(path);
		Resource modelResource = rs.getResource(uri, true);
		EcoreUtil.resolveAll(rs);
		
		if(modelResource == null)
			throw new IOException("File did not contain a vaild model.");
		return modelResource;
	}

}
