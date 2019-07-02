package org.emoflon.ibex.gt.hipe.ide.codegen;

import org.emoflon.ibex.gt.editor.ui.builder.GTBuilderExtension;
import org.emoflon.ibex.gt.hipe.runtime.IBeXToHiPEPatternTransformation;
import org.moflon.core.plugins.manifest.ManifestFileUpdater;
import org.moflon.core.utilities.ClasspathUtil;
import org.moflon.core.utilities.LogUtils;

import IBeXLanguage.IBeXLanguagePackage;
import IBeXLanguage.IBeXPatternSet;
import hipe.generator.HiPEGenerator;
import hipe.network.HiPENetwork;
import hipe.pattern.HiPEPatternContainer;
import hipe.searchplan.simple.SimpleSearchPlan;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;

public class GTHiPEBuilderExtension implements GTBuilderExtension{

	public static final String BUILDER_ID = "org.emoflon.ibex.gt.editor.ui.hipe.builder";
	private static Logger logger = Logger.getLogger(GTHiPEBuilderExtension.class);
	
	private String packageName;
	private String packagePath;
	
	@Override
	public void run(IProject project) {
		try {
			project.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		} catch (CoreException e) {
			LogUtils.error(logger, e.getMessage());
		}
	}

	@Override
	public void run(IProject project, IPath packagePath) {
		LogUtils.info(logger, "## HiPE ## Generating HiPE-Engine code..");
		double tic = System.currentTimeMillis();
		
		packageName = packagePath.toString().replace("/", ".");
		
		if(project.getFullPath().makeAbsolute().toPortableString().equals(packagePath.makeAbsolute().toPortableString())) {
			this.packagePath = packagePath.makeAbsolute().toPortableString();
		}else {
			this.packagePath = project.getFullPath().makeAbsolute().toPortableString();
		}
		
		LogUtils.info(logger, "Loading IBeX patterns..");
		String patternPath = this.packagePath+"//src-gen//" + packageName + "//api//ibex-patterns.xmi";
		IBeXPatternSet ibexPatterns = loadIBeXPatterns(patternPath);
		if(ibexPatterns == null)
			return;
		
		IFile file = project.getFile(patternPath);
		this.packagePath = file.getLocation().uptoSegment(file.getLocation().segmentCount()-5).makeAbsolute().toPortableString();
		
		LogUtils.info(logger, "Cleaning old code..");
		cleanOldCode();
		
		/*
		LogUtils.info(logger, "Creating jar directory..");
		createNewDirectory(this.packagePath+"/jars");
		File jarsDir1 = findJarsDirectory();
		File jarsDir2 = new File(this.packagePath+"/jars");
		
		LogUtils.info(logger, "Copying jars..");
		copyDirectoryContents(jarsDir1, jarsDir2);
		*/
		LogUtils.info(logger, "Updating Manifest & build properties..");
		updateManifest(this.packagePath, project);
		updateBuildProperties(this.packagePath);
		IFolder srcGenFolder = project.getFolder("src-gen");
		try {
			ClasspathUtil.makeSourceFolderIfNecessary(srcGenFolder);
		} catch (CoreException e1) {
			// TODO Auto-generated catch block
			LogUtils.error(logger, e1.getMessage());
		}
		
		LogUtils.info(logger, "Converting IBeX to HiPE Patterns..");
		IBeXToHiPEPatternTransformation transformation = new IBeXToHiPEPatternTransformation();
		HiPEPatternContainer container = transformation.transform(ibexPatterns);
		
		LogUtils.info(logger, "Creating search plan & generating Rete network..");
		SimpleSearchPlan searchPlan = new SimpleSearchPlan(container);
		searchPlan.generateSearchPlan();
		HiPENetwork network = searchPlan.getNetwork();
		
		LogUtils.info(logger, "Generating Code..");
		HiPEGenerator.generateCode(packageName+".", this.packagePath, network);
		
		double toc = System.currentTimeMillis();
		LogUtils.info(logger, "Code generation completed in "+ (toc-tic)/1000.0 + " seconds.");	
		
		LogUtils.info(logger, "Saving HiPE patterns and HiPE network..");
		String debugFolder = this.packagePath + "/debug";
		createNewDirectory(debugFolder);
		saveResource(container, debugFolder+"/hipe-patterns.xmi");
		saveResource(network, debugFolder+"/hipe-network.xmi");
		
		LogUtils.info(logger, "Refreshing workspace and cleaning build ..");
		try {
			project.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			project.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor());
		} catch (CoreException e) {
			LogUtils.error(logger, e.getMessage());
		}
		
		LogUtils.info(logger, "## HiPE ## --> HiPE build complete!");
	}
	
	private void updateManifest(String packagePath, IProject project) {
		try {
			IFile manifest = ManifestFileUpdater.getManifestFile(project);
			ManifestHelper helper = new ManifestHelper();
			helper.loadManifest(manifest);
			/*
			if(!helper.containsSection("Bundle-ClassPath")) {
				helper.appendSection("Bundle-ClassPath");
			}
			
			if(!helper.sectionContainsContent("Bundle-ClassPath", "jars/")) {
				helper.addContentToSection("Bundle-ClassPath", "jars/");
			}
			
			if(!helper.sectionContainsContent("Bundle-ClassPath", ".")) {
				helper.addContentToSection("Bundle-ClassPath", ".");
			}
			*/
			File rawManifest = new File(this.packagePath+"/"+manifest.getFullPath().removeFirstSegments(1).toPortableString());
			
			helper.updateManifest(rawManifest);
			
		} catch (CoreException | IOException e) {
			LogUtils.error(logger, "Failed to update MANIFEST.MF \n"+e.getMessage());
		}
	}
	
	private void updateBuildProperties(String packagePath) {
		File buildProps = new File(this.packagePath+"/build.properties");
		BuildPropertiesHelper helper = new BuildPropertiesHelper();
		try {
			helper.loadProperties(buildProps);
			
			if(!helper.containsSection("source..")) {
				helper.appendSection("source..");
			}
			
			if(!helper.sectionContainsContent("source..", "src-gen/")) {
				helper.addContentToSection("source..", "src-gen/");
			}
			/*
			if(!helper.containsSection("jars.extra.classpath")) {
				helper.appendSection("jars.extra.classpath");
			}
			
			if(!helper.sectionContainsContent("jars.extra.classpath", "jars/akka-actor_2.12-2.5.19.jar")) {
				helper.addContentToSection("jars.extra.classpath", "jars/akka-actor_2.12-2.5.19.jar");
			}
			
			if(!helper.sectionContainsContent("jars.extra.classpath", "jars/config-1.3.3.jar")) {
				helper.addContentToSection("jars.extra.classpath", "jars/config-1.3.3.jar");
			}
			
			if(!helper.sectionContainsContent("jars.extra.classpath", "jars/scala-java8-compat_2.12-0.8.0.jar")) {
				helper.addContentToSection("jars.extra.classpath", "jars/scala-java8-compat_2.12-0.8.0.jar");
			}
			
			if(!helper.sectionContainsContent("jars.extra.classpath", "jars/scala-library-2.12.8.jar")) {
				helper.addContentToSection("jars.extra.classpath", "jars/scala-library-2.12.8.jar");
			}
			*/
			helper.updateProperties(buildProps);
			
		} catch (CoreException | IOException e) {
			LogUtils.error(logger, "Failed to update build.properties \n"+e.getMessage());
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
				LogUtils.error(logger, "Directory in: "+path+" could not be created!");
			}else {
				LogUtils.info(logger, "--> Directory in: "+path+" created!");
			}
		}else {
			LogUtils.info(logger, "--> Directory already present in: "+path+", nothing to do.");
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
		    	LogUtils.error(logger, "Failed to copy required jars. \n"+e.getMessage());
			} 
		});
	}
	
	private void cleanOldCode() {
		File dir = new File(this.packagePath+"/src-gen/" + packageName + "/hipe");
		if(dir.exists()) {
			LogUtils.info(logger, "--> Cleaning old source files in root folder: "+this.packagePath+"/src-gen/" + packageName + "/hipe");
			if(!deleteDirectory(dir)) {
				LogUtils.error(logger, "Folder couldn't be deleted!");
			}
		} else {
			LogUtils.info(logger, "--> No previously generated code found, nothing to do!");
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
	
	private static IBeXPatternSet loadIBeXPatterns(String path) {
		Resource res = null;
		try {
			res = loadResource(path);
		} catch (Exception e) {
			LogUtils.error(logger, "Couldn't load ibex pattern set: \n" + e.getMessage());
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
	
	public static void saveResource(EObject model, String path) {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi-resource", new XMIResourceFactoryImpl());
		ResourceSet rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
		
		URI uri = URI.createFileURI(path);
		Resource modelResource = rs.createResource(uri);
		modelResource.getContents().add(model);
		
		Map<Object, Object> saveOptions = ((XMIResource)modelResource).getDefaultSaveOptions();
		saveOptions.put(XMIResource.OPTION_ENCODING,"UTF-8");
		saveOptions.put(XMIResource.OPTION_USE_XMI_TYPE, Boolean.TRUE);
		saveOptions.put(XMIResource.OPTION_SAVE_TYPE_INFORMATION,Boolean.TRUE);
		saveOptions.put(XMIResource.OPTION_SCHEMA_LOCATION_IMPLEMENTATION, Boolean.TRUE);
		
		try {
			((XMIResource)modelResource).save(saveOptions);
		} catch (IOException e) {
			LogUtils.error(logger, "Couldn't save debug resource: \n "+e.getMessage());
		}
	}

}
