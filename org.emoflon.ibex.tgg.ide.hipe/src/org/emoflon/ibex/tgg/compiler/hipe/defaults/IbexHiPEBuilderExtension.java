package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.emoflon.ibex.gt.hipe.runtime.IBeXToHiPEPatternTransformation;
import org.emoflon.ibex.tgg.compiler.defaults.DefaultFilesGenerator;
import org.emoflon.ibex.tgg.compiler.transformations.patterns.ContextPatternTransformation;
import org.emoflon.ibex.tgg.ide.admin.BuilderExtension;
import org.emoflon.ibex.tgg.ide.admin.IbexTGGBuilder;
import org.emoflon.ibex.tgg.operational.csp.constraints.factories.RuntimeTGGAttrConstraintFactory;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
import org.emoflon.ibex.tgg.operational.strategies.OperationalStrategy;
import org.moflon.core.plugins.manifest.ManifestFileUpdater;
import org.moflon.core.utilities.LogUtils;
import org.moflon.tgg.mosl.tgg.TripleGraphGrammarFile;

import IBeXLanguage.IBeXPatternSet;
import hipe.generator.HiPEGenerator;
import hipe.network.HiPENetwork;
import hipe.pattern.HiPEPatternContainer;
import hipe.searchplan.simple.SimpleSearchPlan;

public class IbexHiPEBuilderExtension implements BuilderExtension {

	private Logger logger = Logger.getLogger(IbexHiPEBuilderExtension.class);
	
	private static final String ENGINE = "HiPETGGEngine";
	private static final String IMPORT = "import org.emoflon.ibex.tgg.runtime.engine.HiPETGGEngine;";
	
	private String projectName;
	private String projectPath;
	
	private List<String> metaModelImports;
	
	@Override
	public void run(IbexTGGBuilder builder, TripleGraphGrammarFile editorModel, TripleGraphGrammarFile flattenedEditorModel) {
		LogUtils.info(logger, "Starting HiPE TGG builder ... ");
		double tic = System.currentTimeMillis();
		
		projectName = builder.getProject().getName();
		projectPath = projectName;
		
		metaModelImports = flattenedEditorModel.getImports().stream()
				.map(imp -> imp.getName())
				.collect(Collectors.toList());
		
		LogUtils.info(logger, "Building TGG API classes...");
		try {
			
			builder.createDefaultRunFile("MODELGEN_App_HiPE", (projectName, fileName) 
					-> DefaultFilesGenerator.generateModelGenFile(projectName, fileName, ENGINE, IMPORT));
			builder.createDefaultRunFile("SYNC_App_HiPE", (projectName, fileName) 
					-> DefaultFilesGenerator.generateSyncAppFile(projectName, fileName, ENGINE, IMPORT));
			builder.createDefaultRunFile("INITIAL_FWD_App_HiPE", (projectName, fileName) 
					-> DefaultFilesGenerator.generateInitialFwdAppFile(projectName, fileName, ENGINE, IMPORT));
			builder.createDefaultRunFile("INITIAL_BWD_App_HiPE", (projectName, fileName) 
					-> DefaultFilesGenerator.generateInitialBwdAppFile(projectName, fileName, ENGINE, IMPORT));
			builder.createDefaultRunFile("CC_App_HiPE", (projectName, fileName) 
					-> DefaultFilesGenerator.generateCCAppFile(projectName, fileName, ENGINE, IMPORT));
			builder.createDefaultRunFile("CO_App_HiPE", (projectName, fileName) 
					-> DefaultFilesGenerator.generateCOAppFile(projectName, fileName, ENGINE, IMPORT));
			builder.createDefaultRunFile("FWD_OPT_App_HiPE", (projectName, fileName) 
					-> DefaultFilesGenerator.generateFWDOptAppFile(projectName, fileName, ENGINE, IMPORT));
			builder.createDefaultRunFile("BWD_OPT_App_HiPE", (projectName, fileName) 
					-> DefaultFilesGenerator.generateBWDOptAppFile(projectName, fileName, ENGINE, IMPORT));
			builder.createDefaultRunFile("_RegistrationHelper", (projectName, fileName)
					-> DefaultFilesGenerator.generateRegHelperFile(projectName));
			builder.enforceDefaultRunFile("_SchemaBasedAutoRegistration", (projectName, fileName)
					-> DefaultFilesGenerator.generateSchemaAutoRegFile(projectName, editorModel));
		} catch (CoreException e) {
			LogUtils.error(logger, e);
		}
		
		LogUtils.info(logger, "Building TGG options...");
		IbexOptions opt = createIbexOptions(projectName, projectPath);
		
		LogUtils.info(logger, "Building TGG operational strategy...");
		OperationalStrategy strategy = null;
		try {
			strategy = new OperationalStrategyImpl(opt, metaModelImports);
		} catch (IOException e) {
			LogUtils.error(logger, e);
			return;
		}
		
		LogUtils.info(logger, "Compiling ibex patterns from TGG patterns...");
		ContextPatternTransformation compiler = new ContextPatternTransformation(opt, strategy);
		IBeXPatternSet ibexPatterns = compiler.transform();
		
		projectPath = builder.getProject().getLocation().toPortableString();
		LogUtils.info(logger, "Cleaning old code..");
		cleanOldCode();
		
		LogUtils.info(logger, "Creating jar directory..");
		createNewDirectory(projectPath+"/jars");
		File jarsDir1 = findJarsDirectory();
		File jarsDir2 = new File(projectPath+"/jars");
		
		LogUtils.info(logger, "Copying jars..");
		copyDirectoryContents(jarsDir1, jarsDir2);
		
		LogUtils.info(logger, "Updating Manifest & build properties..");
		updateManifest(projectPath, builder.getProject());
		updateBuildProperties(projectPath);
		
		LogUtils.info(logger, "Converting IBeX to HiPE Patterns..");
		IBeXToHiPEPatternTransformation transformation = new IBeXToHiPEPatternTransformation();
		HiPEPatternContainer container = transformation.transform(ibexPatterns);
		
		LogUtils.info(logger, "Creating search plan & generating Rete network..");
		SimpleSearchPlan searchPlan = new SimpleSearchPlan(container);
		searchPlan.generateSearchPlan();
		HiPENetwork network = searchPlan.getNetwork();
		
		LogUtils.info(logger, "Generating Code..");
		HiPEGenerator.generateCode(projectName+".", projectPath, network);
		
		double toc = System.currentTimeMillis();
		LogUtils.info(logger, "Code generation completed in "+ (toc-tic)/1000.0 + " seconds.");	
		
		
		LogUtils.info(logger, "## HiPE ## --> HiPE build complete!");
	}
	
	
	@SuppressWarnings("unchecked")
	public IbexOptions createIbexOptions(String projectName, String projectPath) {
		IbexOptions options = new IbexOptions();
		options.projectName(projectName);
		options.projectPath(projectPath);
		options.debug(false);
		/*
		String fullyQualifiedName = "org.emoflon.ibex.tgg.operational.csp.constraints.factories." 
										+ projectName.toLowerCase() 
										+ ".UserDefinedRuntimeTGGAttrConstraintFactory";
		
		Class<? extends RuntimeTGGAttrConstraintFactory> constraintFactory = null;
		try {
			constraintFactory = (Class<? extends RuntimeTGGAttrConstraintFactory>) Class.forName(fullyQualifiedName);
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			LogUtils.error(logger, e1);
			return options;
		}
		
		try {
			options.userDefinedConstraints(constraintFactory.newInstance());
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			LogUtils.error(logger, e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			LogUtils.error(logger, e);
		}
		*/
		return options;
	}
	
	private void cleanOldCode() {
		File dir = new File(projectPath+"/src-gen/" + projectName + "/hipe");
		if(dir.exists()) {
			LogUtils.info(logger, "--> Cleaning old source files in root folder: "+projectPath+"/src-gen/" + projectName + "/hipe");
			if(!deleteDirectory(dir)) {
				LogUtils.info(logger, "ERROR: Folder couldn't be deleted!");

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
		LogUtils.info(logger, "Jars directory in org.emoflon.ibex.tgg.ide.hipe project not found!");
		return null;
	}
	
	private void updateManifest(String packagePath, IProject project) {
		try {
			IFile manifest = ManifestFileUpdater.getManifestFile(project);
			File rawManifest = new File(packagePath+"/"+manifest.getFullPath().removeFirstSegments(1).toPortableString());
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
				LogUtils.info(logger, "--> Manifest already up to date.");
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
			LogUtils.info(logger, "ERROR: Failed to update MANIFEST.MF.");
		}
	}
	
	private void updateBuildProperties(String packagePath) {
		File buildProps = new File(packagePath+"/build.properties");
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
				LogUtils.info(logger, "--> build.properties already up to date.");
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
	
	private static boolean deleteDirectory(File dir) {
		File[] contents  = dir.listFiles();
		if(contents != null) {
			for(File file : contents) {
				deleteDirectory(file);
			}
		}
		return dir.delete();
	}
	
	private void createNewDirectory(String path) {
		File dir = new File(path);
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				LogUtils.info(logger, "ERROR: Directory in: "+path+" could not be created!");
			}else {
				LogUtils.info(logger, "--> Directory in: "+path+" created!");
			}
		}else {
			LogUtils.info(logger, "--> Directory already present in: "+path+", nothing to do.");
		}
	}
}
