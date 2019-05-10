package org.emoflon.ibex.gt.hipe.ide.codegen;

import org.emoflon.ibex.gt.editor.ui.builder.GTBuilderExtension;
import org.moflon.core.plugins.manifest.ManifestFileUpdater;
import org.eclipse.core.runtime.IPath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Manifest;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IProject;

public class GTHiPEBuilderExtension implements GTBuilderExtension{

	public static final String BUILDER_ID = "org.emoflon.ibex.gt.editor.ui.hipe.builder";
	
	private String packageName;
	
	@Override
	public void run(IProject project) {
		Logger.getRootLogger().info("HiPE plugin extension test 1");
	}

	@Override
	public void run(IProject project, IPath packagePath) {
		Logger.getRootLogger().info("HiPE plugin extension test 1");
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
	
	private void log(String lg) {
		Logger.getRootLogger().info(lg);
	}

}
