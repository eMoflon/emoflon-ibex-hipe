package org.emoflon.ibex.tgg.compiler.hipe.defaults

import org.emoflon.ibex.tgg.compiler.defaults.DefaultFilesGenerator
import org.moflon.core.utilities.MoflonUtil

class HiPEFilesGenerator extends DefaultFilesGenerator {
	
	public static final String DEFAULT_REGISTRATION_HELPER = "_DefaultRegistrationHelper";
	public static final String MODELGEN_APP = "MODELGEN_App"; 
	public static final String SYNC_APP = "SYNC_App";
	public static final String INITIAL_FWD_APP = "INITIAL_FWD_App";
	public static final String INITIAL_BWD_APP = "INITIAL_BWD_App";
	public static final String CC_APP = "CC_App";
	public static final String CO_APP = "CO_App";
	public static final String FWD_OPT_APP = "FWD_OPT_App";
	public static final String BWD_OPT_APP = "BWD_OPT_App";
	public static final String REGISTRATION_HELPER = "HiPERegistrationHelper";
	 	
	def static String generateRegHelperFile(String projectName, String srcProject, String trgProject, String srcPkg, String trgPkg) {
		'''
			package org.emoflon.ibex.tgg.run.«MoflonUtil.lastCapitalizedSegmentOf(projectName).toLowerCase».config;
			
			import java.io.File;
			import java.io.IOException;
			
			import org.eclipse.emf.common.util.URI;
			import org.eclipse.emf.ecore.EPackage;
			import org.eclipse.emf.ecore.resource.Resource;
			import org.eclipse.emf.ecore.resource.ResourceSet;
			import org.emoflon.ibex.tgg.operational.csp.constraints.factories.«MoflonUtil.lastCapitalizedSegmentOf(projectName).toLowerCase».UserDefinedRuntimeTGGAttrConstraintFactory;
			import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
			import org.emoflon.ibex.tgg.operational.strategies.OperationalStrategy;
			import org.emoflon.ibex.tgg.operational.strategies.opt.BWD_OPT;
			import org.emoflon.ibex.tgg.operational.strategies.opt.FWD_OPT;
			import org.emoflon.ibex.tgg.runtime.democles.DemoclesTGGEngine;
			import org.emoflon.ibex.tgg.runtime.hipe.HiPETGGEngine;
			import org.emoflon.ibex.tgg.compiler.defaults.IRegistrationHelper;
			
			import «projectName».«projectName»Package;
			import «projectName».impl.«projectName»PackageImpl;
			import «srcPkg».impl.«srcPkg.toFirstUpper»PackageImpl;
			import «trgPkg».impl.«trgPkg.toFirstUpper»PackageImpl;
			
			public class «REGISTRATION_HELPER» implements IRegistrationHelper {
				
				/** Create default options **/
				public final void setWorkspaceRootDirectory(OperationalStrategy strategy, ResourceSet resourceSet) throws IOException {
					String root = "../";
					String workspace = strategy.getOptions().workspacePath();
					if(workspace != null && !workspace.isEmpty()) {
						root = workspace;
					}
					URI key = URI.createPlatformResourceURI("/", true);
					URI value = URI.createFileURI(new File(root).getCanonicalPath() + File.separatorChar);
					resourceSet.getURIConverter().getURIMap().put(key, value);
				}
			
				/** Load and register source and target metamodels */
				public void registerMetamodels(ResourceSet rs, OperationalStrategy strategy) throws IOException {
					
					// Set correct workspace root
					setWorkspaceRootDirectory(strategy, rs);
					
					// Load and register source and target metamodels
					EPackage «srcProject.toLowerCase»Pack = null;
					EPackage «trgProject.toLowerCase»Pack = null;
					EPackage «projectName.toLowerCase»Pack = null;
					
					if(strategy instanceof FWD_OPT) {
						Resource res = strategy.loadResource("platform:/resource/«trgProject»/model/«trgProject».ecore");
						«trgProject.toLowerCase»Pack = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
						
						res = strategy.loadResource("platform:/resource/«projectName»/model/«projectName».ecore");
						«projectName.toLowerCase»Pack = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
					}
							
					if(strategy instanceof BWD_OPT) {
						Resource res = strategy.loadResource("platform:/resource/«srcProject»/model/«srcProject».ecore");
						«srcProject.toLowerCase»Pack = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
						
						res = strategy.loadResource("platform:/resource/«projectName»/model/«projectName».ecore");
						«projectName.toLowerCase»Pack = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
					}

					if(«srcProject.toLowerCase»Pack == null)
						«srcProject.toLowerCase»Pack = «srcPkg.toFirstUpper»PackageImpl.init();
							
					if(«trgProject.toLowerCase»Pack == null)
						«trgProject.toLowerCase»Pack = «trgPkg.toFirstUpper»PackageImpl.init();
					
					if(«projectName.toLowerCase»Pack == null) {
						«projectName.toLowerCase»Pack = «projectName»PackageImpl.init();
						rs.getPackageRegistry().put("platform:/resource/«projectName»/model/«projectName».ecore", «projectName»Package.eINSTANCE);
						rs.getPackageRegistry().put("platform:/plugin/«projectName»/model/«projectName».ecore", «projectName»Package.eINSTANCE);
					}
						
					rs.getPackageRegistry().put("platform:/resource/«srcProject»/model/«srcProject».ecore", «srcProject.toLowerCase»Pack);
				    rs.getPackageRegistry().put("platform:/plugin/«srcProject»/model/«srcProject».ecore", «srcProject.toLowerCase»Pack);	
						
					rs.getPackageRegistry().put("platform:/resource/«trgProject»/model/«trgProject».ecore", «trgProject.toLowerCase»Pack);
					rs.getPackageRegistry().put("platform:/plugin/«trgProject»/model/«trgProject».ecore", «trgProject.toLowerCase»Pack);
				}
			
				/** Create default options **/
				public IbexOptions createIbexOptions() {
					IbexOptions options = new IbexOptions();
					options.setBlackInterpreter(new HiPETGGEngine());
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