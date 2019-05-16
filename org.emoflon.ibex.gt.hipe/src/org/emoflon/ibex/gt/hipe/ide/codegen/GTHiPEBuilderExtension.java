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

import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
		Logger.getRootLogger().info("Generating HiPE-Engine code..");
		double tic = System.currentTimeMillis();
		
		packageName = packagePath.toString().replace("/", ".");
		
		if(project.getFullPath().makeAbsolute().toPortableString().equals(packagePath.makeAbsolute().toPortableString())) {
			this.packagePath = packagePath.makeAbsolute().toPortableString();
		}else {
			this.packagePath = project.getFullPath().makeAbsolute().toPortableString();
		}
		String patternPath = this.packagePath+"//src-gen//" + packageName + "//api//ibex-patterns.xmi";
		
		IBeXPatternSet ibexPatterns = loadIBeXPatterns(patternPath);
		
		if(ibexPatterns == null)
			return;
		
		IBeXToHiPEPatternTransformation transformation = new IBeXToHiPEPatternTransformation();
		HiPEPatternContainer container = transformation.transform(ibexPatterns);
		
		IFile file = project.getFile(patternPath);
		this.packagePath = file.getLocation().uptoSegment(file.getLocation().segmentCount()-5).makeAbsolute().toPortableString();
		
		File dir = new File(this.packagePath+"/src-gen/" + packageName + "/hipe");
		if(dir.exists()) {
			log("Cleaning old source files in root folder: "+this.packagePath+"/src-gen/" + packageName + "/hipe");
			dir.delete();
			if(deleteDirectory(dir)) {
				log("Folder deleted!");
			}else {
				log("Folder not deleted..");
			}
		}
		
		
		SimpleSearchPlan searchPlan = new SimpleSearchPlan(container);
		searchPlan.generateSearchPlan();
		HiPENetwork network = searchPlan.getNetwork();
		
		
		HiPEGenerator.generateCode(packageName+".", this.packagePath, network);
		
		double toc = System.currentTimeMillis();
		Logger.getRootLogger().info(".. complete, took "+ (toc-tic)/1000.0 + " seconds.");	
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
	
	private boolean processManifestForProject(final Manifest manifest, IProject project) {
		List<String> dependencies = new ArrayList<String>();
		dependencies.addAll(Arrays.asList("org.emoflon.ibex.common", "org.emoflon.ibex.gt"));
		//collectEngineExtensions().forEach(engine -> dependencies.addAll(engine.getDependencies()));

		boolean changedBasics = ManifestFileUpdater.setBasicProperties(manifest, project.getName());
		if (changedBasics) {
			log("Initialized MANIFEST.MF.");
		}

		boolean updatedDependencies = ManifestFileUpdater.updateDependencies(manifest, dependencies);
		if (updatedDependencies) {
			log("Updated dependencies");
		}

		return changedBasics || updatedDependencies;
	}
	
	private boolean processManifestForPackage(final Manifest manifest) {
		String apiPackageName = (packageName.equals("") ? "" : packageName + ".") + "api";
		boolean updateExports = ManifestFileUpdater.updateExports(manifest,
				Arrays.asList(apiPackageName, apiPackageName + ".matches", apiPackageName + ".rules"));
		if (updateExports) {
			log("Updated exports");
		}
		return updateExports;
	}
	
	private static void log(String lg) {
		Logger.getRootLogger().info(lg);
	}
	
	private static IBeXPatternSet loadIBeXPatterns(String path) {
		Resource res = null;
		try {
			res = loadResource(path);
		} catch (Exception e) {
			log("Couldn't load ibex pattern set: \n" + e.getMessage());
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
