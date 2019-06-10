package org.emoflon.ibex.tgg.compiler.hipe.defaults

import org.emoflon.ibex.tgg.compiler.defaults.DefaultFilesGenerator
import org.moflon.core.utilities.MoflonUtil

class HiPEFilesGenerator extends DefaultFilesGenerator {
	
	def static String generateRegHelperFile(String projectName, String src, String trg) {
		val srcProject = src.toFirstUpper
		val trgProject = trg.toFirstUpper
		'''
			package org.emoflon.ibex.tgg.run.«MoflonUtil.lastCapitalizedSegmentOf(projectName).toLowerCase»;
			
			import java.io.IOException;
			
			import org.eclipse.emf.ecore.EPackage;
			import org.eclipse.emf.ecore.resource.Resource;
			import org.eclipse.emf.ecore.resource.ResourceSet;
			import org.emoflon.ibex.tgg.operational.csp.constraints.factories.«MoflonUtil.lastCapitalizedSegmentOf(projectName).toLowerCase».UserDefinedRuntimeTGGAttrConstraintFactory;
			import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
			import org.emoflon.ibex.tgg.operational.strategies.OperationalStrategy;
			import org.emoflon.ibex.tgg.operational.strategies.opt.BWD_OPT;
			import org.emoflon.ibex.tgg.operational.strategies.opt.FWD_OPT;
			
			import «projectName».«projectName»Package;
			import «projectName».impl.«projectName»PackageImpl;
			import «src».impl.«srcProject»PackageImpl;
			import «trg».impl.«trgProject»PackageImpl;
			
			public class _RegistrationHelper {
			
				/** Load and register source and target metamodels */
				public static void registerMetamodels(ResourceSet rs, OperationalStrategy strategy) throws IOException {
					// Load and register source and target metamodels
					EPackage «srcProject.toFirstLower»Pack = null;
					EPackage «trgProject.toFirstLower»Pack = null;
					
					«projectName»PackageImpl.init();
					rs.getPackageRegistry().put("platform:/resource/«projectName»/model/«projectName».ecore", «projectName»Package.eINSTANCE);
					rs.getPackageRegistry().put("platform:/plugin/«projectName»/model/«projectName».ecore", «projectName»Package.eINSTANCE);
							
					if(strategy instanceof FWD_OPT) {
						Resource res = strategy.loadResource("platform:/resource/«trgProject»/model/«trgProject».ecore");
						«trgProject.toFirstLower»Pack = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
					}
							
					if(strategy instanceof BWD_OPT) {
						Resource res = strategy.loadResource("platform:/resource/«srcProject»/model/«srcProject».ecore");
						«srcProject.toFirstLower»Pack = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
					}
					
					if(«srcProject.toFirstLower»Pack == null)
						«srcProject.toFirstLower»Pack = «srcProject»PackageImpl.init();
							
					if(«trgProject.toFirstLower»Pack == null)
						«trgProject.toFirstLower»Pack = «trgProject»PackageImpl.init();
						
					rs.getPackageRegistry().put("platform:/resource/«srcProject»/model/«srcProject».ecore", «srcProject.toFirstLower»Pack);
				    rs.getPackageRegistry().put("platform:/plugin/«srcProject»/model/«srcProject».ecore", «srcProject.toFirstLower»Pack);	
						
					rs.getPackageRegistry().put("platform:/resource/«trgProject»/model/«trgProject».ecore", «trgProject.toFirstLower»Pack);
					rs.getPackageRegistry().put("platform:/plugin/«trgProject»/model/«trgProject».ecore", «trgProject.toFirstLower»Pack);
				}
			
				/** Create default options **/
				public static IbexOptions createIbexOptions() {
					IbexOptions options = new IbexOptions();
					options.projectName("«MoflonUtil.lastCapitalizedSegmentOf(projectName)»");
					options.projectPath("«projectName»");
					options.debug(false);
					options.userDefinedConstraints(new UserDefinedRuntimeTGGAttrConstraintFactory());
					return options;
				}
			}
		'''
	}
}