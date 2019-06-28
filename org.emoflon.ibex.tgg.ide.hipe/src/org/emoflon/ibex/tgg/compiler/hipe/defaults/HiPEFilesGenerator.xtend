package org.emoflon.ibex.tgg.compiler.hipe.defaults

import org.emoflon.ibex.tgg.compiler.defaults.DefaultFilesGenerator
import org.moflon.core.utilities.MoflonUtil

class HiPEFilesGenerator extends DefaultFilesGenerator {
	
	public static final String DEFAULT_REGISTRATION_HELPER = "DefaultRegistrationHelper";
	public static final String MODELGEN_APP = "MODELGEN_App"; 
	public static final String SYNC_APP = "SYNC_App";
	public static final String INITIAL_FWD_APP = "INITIAL_FWD_App";
	public static final String INITIAL_BWD_APP = "INITIAL_BWD_App";
	public static final String CC_APP = "CC_App";
	public static final String CO_APP = "CO_App";
	public static final String FWD_OPT_APP = "FWD_OPT_App";
	public static final String BWD_OPT_APP = "BWD_OPT_App";
	public static final String REGISTRATION_HELPER = "_RegistrationHelper";
	public static final String SCHEMA_BASED_AUTO_REG = "_SchemaBasedAutoRegistration";
	 	
	def static String generateRegHelperFile(String projectName) {
		'''
			package org.emoflon.ibex.tgg.run.«MoflonUtil.lastCapitalizedSegmentOf(projectName).toLowerCase»;
			
			import java.io.IOException;
				
			import org.eclipse.emf.ecore.resource.ResourceSet;
			import org.emoflon.ibex.tgg.operational.csp.constraints.factories.«MoflonUtil.lastCapitalizedSegmentOf(projectName).toLowerCase».UserDefinedRuntimeTGGAttrConstraintFactory;
			import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
			import org.emoflon.ibex.tgg.operational.strategies.OperationalStrategy;
			import org.emoflon.ibex.tgg.run.«MoflonUtil.lastCapitalizedSegmentOf(projectName).toLowerCase».DefaultRegistrationHelper;
			
			public class _RegistrationHelper {
			
				/** Load and register source and target metamodels */
				public static void registerMetamodels(ResourceSet rs, OperationalStrategy strategy) throws IOException {
					// Replace to register generated code or handle other URI-related requirements
					DefaultRegistrationHelper.registerMetamodels(rs, strategy);
				}
			
				/** Create default options **/
				public static IbexOptions createIbexOptions() {
					return DefaultRegistrationHelper.createIbexOptions();
				}
			}
		'''
	}
	
	def static String generateDefaultRegHelperFile(String projectName, String srcProject, String trgProject, String srcPkg, String trgPkg) {
		'''
			package org.emoflon.ibex.tgg.run.«MoflonUtil.lastCapitalizedSegmentOf(projectName).toLowerCase»;
			
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
			import org.emoflon.ibex.tgg.runtime.hipe.HiPETGGEngine;
			
			import «projectName».«projectName»Package;
			import «projectName».impl.«projectName»PackageImpl;
			import «srcPkg».impl.«srcPkg.toFirstUpper»PackageImpl;
			import «trgPkg».impl.«trgPkg.toFirstUpper»PackageImpl;
			
			public class «DEFAULT_REGISTRATION_HELPER» {
				
				/** Create default options **/
				public static final void setWorkspaceRootDirectory(ResourceSet resourceSet) throws IOException {
					final String root = "../";
					URI key = URI.createPlatformResourceURI("/", true);
					URI value = URI.createFileURI(new File(root).getCanonicalPath() + File.separatorChar);
					resourceSet.getURIConverter().getURIMap().put(key, value);
				}
			
				/** Load and register source and target metamodels */
				public static void registerMetamodels(ResourceSet rs, OperationalStrategy strategy) throws IOException {
					
					// Set correct workspace root
					setWorkspaceRootDirectory(rs);
					
					// Load and register source and target metamodels
					EPackage «srcProject.toLowerCase»Pack = null;
					EPackage «trgProject.toLowerCase»Pack = null;
					
					«projectName»PackageImpl.init();
					rs.getPackageRegistry().put("platform:/resource/«projectName»/model/«projectName».ecore", «projectName»Package.eINSTANCE);
					rs.getPackageRegistry().put("platform:/plugin/«projectName»/model/«projectName».ecore", «projectName»Package.eINSTANCE);
							
					if(strategy instanceof FWD_OPT) {
						Resource res = strategy.loadResource("platform:/resource/«trgProject»/model/«trgProject».ecore");
						«trgProject.toLowerCase»Pack = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
					}
							
					if(strategy instanceof BWD_OPT) {
						Resource res = strategy.loadResource("platform:/resource/«srcProject»/model/«srcProject».ecore");
						«srcProject.toLowerCase»Pack = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
					}
					
					if(«srcProject.toLowerCase»Pack == null)
						«srcProject.toLowerCase»Pack = «srcPkg.toFirstUpper»PackageImpl.init();
							
					if(«trgProject.toLowerCase»Pack == null)
						«trgProject.toLowerCase»Pack = «trgPkg.toFirstUpper»PackageImpl.init();
						
					rs.getPackageRegistry().put("platform:/resource/«srcProject»/model/«srcProject».ecore", «srcProject.toLowerCase»Pack);
				    rs.getPackageRegistry().put("platform:/plugin/«srcProject»/model/«srcProject».ecore", «srcProject.toLowerCase»Pack);	
						
					rs.getPackageRegistry().put("platform:/resource/«trgProject»/model/«trgProject».ecore", «trgProject.toLowerCase»Pack);
					rs.getPackageRegistry().put("platform:/plugin/«trgProject»/model/«trgProject».ecore", «trgProject.toLowerCase»Pack);
				}
			
				/** Create default options **/
				public static IbexOptions createIbexOptions() {
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