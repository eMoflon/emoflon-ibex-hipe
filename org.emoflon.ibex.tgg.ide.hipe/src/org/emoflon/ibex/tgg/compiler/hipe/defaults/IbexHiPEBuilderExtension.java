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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.emoflon.ibex.gt.hipe.ide.codegen.BuildPropertiesHelper;
import org.emoflon.ibex.gt.hipe.ide.codegen.ManifestHelper;
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

		LogUtils.info(logger, "Building TGG options...");
		IbexOptions opt = createIbexOptions(projectName, projectPath);
		
		LogUtils.info(logger, "Building TGG operational strategy...");
		Collection<OperationalStrategy> strategies = new HashSet<>();
		try {
			strategies.add(new HiPESYNC(opt, metaModelImports));
//			strategies.add(new HiPECC(opt, metaModelImports));
//			strategies.add(new HiPECO(opt, metaModelImports));
		} catch (IOException e) {
			LogUtils.error(logger, e);
			return;
		}

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
		
		
		for(OperationalStrategy strategy : strategies) {
			LogUtils.info(logger, strategy.getClass().getName() + ": Compiling ibex patterns from TGG patterns...");
			ContextPatternTransformation compiler = new ContextPatternTransformation(opt, strategy);
			IBeXPatternSet ibexPatterns = compiler.transform();
			
			LogUtils.info(logger,  strategy.getClass().getName() + ": Converting IBeX to HiPE Patterns..");
			IBeXToHiPEPatternTransformation transformation = new IBeXToHiPEPatternTransformation();
			HiPEPatternContainer container = transformation.transform(ibexPatterns);
			
			LogUtils.info(logger,  strategy.getClass().getName() + ": Creating search plan & generating Rete network..");
			SimpleSearchPlan searchPlan = new SimpleSearchPlan(container);
			searchPlan.generateSearchPlan();
			HiPENetwork network = searchPlan.getNetwork();
			
			LogUtils.info(logger,  strategy.getClass().getName() + ": Generating Code..");
			if(strategy instanceof HiPESYNC) 
				HiPEGenerator.generateCode(projectName+".sync.", projectPath, network);
			else if(strategy instanceof HiPECC) 
				HiPEGenerator.generateCode(projectName+".cc.", projectPath, network);
			else if(strategy instanceof HiPECO) 
				HiPEGenerator.generateCode(projectName+".co.", projectPath, network);
			else
				throw new RuntimeException("Unsupported Operational Strategy detected");
			
			
			double toc = System.currentTimeMillis();
			LogUtils.info(logger,  strategy.getClass().getName() + ": Code generation completed in "+ (toc-tic)/1000.0 + " seconds.");	
			
			LogUtils.info(logger,  strategy.getClass().getName() + ": Saving HiPE patterns and HiPE network..");
			String debugFolder = projectPath + "/debug";
			createNewDirectory(debugFolder);
			saveResource(container, debugFolder+"/" +  strategy.getClass().getName() + "hipe-patterns.xmi");
			saveResource(network, debugFolder+"/" +  strategy.getClass().getName() + "hipe-network.xmi");
		}
		
		LogUtils.info(logger, "Refreshing workspace and cleaning build ..");
		try {
			builder.getProject().getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			builder.getProject().build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor());
		} catch (CoreException e) {
			LogUtils.info(logger, "ERROR: "+e.getMessage());
		}
		
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
			ManifestHelper helper = new ManifestHelper();
			helper.loadManifest(manifest);
			
			if(!helper.containsSection("Bundle-ClassPath")) {
				helper.appendSection("Bundle-ClassPath");
			}
			
			if(!helper.sectionContainsContent("Bundle-ClassPath", "jars/")) {
				helper.addContentToSection("Bundle-ClassPath", "jars/");
			}
			
			if(!helper.sectionContainsContent("Bundle-ClassPath", ".")) {
				helper.addContentToSection("Bundle-ClassPath", ".");
			}
			
			File rawManifest = new File(packagePath+"/"+manifest.getFullPath().removeFirstSegments(1).toPortableString());
			
			helper.updateManifest(rawManifest);
			
		} catch (CoreException | IOException e) {
			LogUtils.info(logger, "ERROR: Failed to update MANIFEST.MF \n"+e.getMessage());
		}
	}
	
	private void updateBuildProperties(String packagePath) {
		File buildProps = new File(packagePath+"/build.properties");
		BuildPropertiesHelper helper = new BuildPropertiesHelper();
		try {
			helper.loadProperties(buildProps);
			
			if(!helper.containsSection("source..")) {
				helper.appendSection("source..");
			}
			
			if(!helper.sectionContainsContent("source..", "src-gen/")) {
				helper.addContentToSection("source..", "src-gen/");
			}
			
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
			
			helper.updateProperties(buildProps);
			
		} catch (CoreException | IOException e) {
			LogUtils.info(logger, "ERROR: Failed to update build.properties \n"+e.getMessage());
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
	
	private void saveResource(EObject model, String path) {
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
			LogUtils.info(logger, "ERROR: Couldn't save debug resource: \n "+e.getMessage());
		}
	}
}
