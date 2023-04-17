package org.emoflon.ibex.tgg.compiler.hipe.defaults

import org.emoflon.ibex.tgg.compiler.codegen.DefaultFilesGenerator
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
	public static final String INTEGRATE_APP = "INTEGRATE_App";
	public static final String REGISTRATION_HELPER = "HiPERegistrationHelper";
	 	
 	def static String generateDefaultRegHelperFile(String projectName) {
		'''
			package org.emoflon.ibex.tgg.run.«MoflonUtil.lastCapitalizedSegmentOf(projectName).toLowerCase».config;
			
			import java.io.IOException;
				
			import org.eclipse.emf.ecore.resource.ResourceSet;
			import org.emoflon.ibex.tgg.runtime.config.IRegistrationHelper;
			import org.emoflon.ibex.tgg.runtime.config.options.IbexOptions;
			import org.emoflon.ibex.tgg.runtime.strategies.modules.IbexExecutable;
			
			public class _DefaultRegistrationHelper implements IRegistrationHelper{
			
				/** Load and register source and target metamodels */
				public void registerMetamodels(ResourceSet rs, IbexExecutable executable) throws IOException {
					// Replace to register generated code or handle other URI-related requirements
					new HiPERegistrationHelper().registerMetamodels(rs, executable);
				}
			
				/** Create default options **/
				public IbexOptions createIbexOptions() {
					return new HiPERegistrationHelper().createIbexOptions();
				}
			}
		'''
	}
	 	
	def static String generateRegHelperFile(String projectName, String srcProject, String trgProject, String srcPkg, String trgPkg) {
		'''
			package org.emoflon.ibex.tgg.run.«MoflonUtil.lastCapitalizedSegmentOf(projectName).toLowerCase».config;
			
			import java.io.File;
			import java.io.IOException;
			
			import org.eclipse.emf.common.util.URI;
			import org.eclipse.emf.ecore.EPackage;
			import org.eclipse.emf.ecore.resource.Resource;
			import org.eclipse.emf.ecore.resource.ResourceSet;
			import org.emoflon.ibex.tgg.operational.csp.constraints.custom.«MoflonUtil.lastCapitalizedSegmentOf(projectName).toLowerCase».*;
			import org.emoflon.ibex.tgg.runtime.config.IRegistrationHelper;
			import org.emoflon.ibex.tgg.runtime.config.options.IbexOptions;
			import org.emoflon.ibex.tgg.runtime.hipe.HiPETGGEngine;
			import org.emoflon.ibex.tgg.runtime.strategies.modules.IbexExecutable;
			import org.emoflon.ibex.tgg.runtime.strategies.opt.BWD_OPT;
			import org.emoflon.ibex.tgg.runtime.strategies.opt.FWD_OPT;
			
			import «projectName».«projectName»Package;
			import «projectName».impl.«projectName»PackageImpl;
			import «srcPkg».impl.«srcPkg.toFirstUpper»PackageImpl;
			import «trgPkg».impl.«trgPkg.toFirstUpper»PackageImpl;
			
			public class «REGISTRATION_HELPER» implements IRegistrationHelper {
				
				/** Create default options **/
				public final void setWorkspaceRootDirectory(ResourceSet resourceSet) throws IOException {
					final String root = "../";
					URI key = URI.createPlatformResourceURI("/", true);
					URI value = URI.createFileURI(new File(root).getCanonicalPath() + File.separatorChar);
					resourceSet.getURIConverter().getURIMap().put(key, value);
				}
			
				/** Load and register source and target metamodels */
				public void registerMetamodels(ResourceSet rs, IbexExecutable executable) throws IOException {
					
					// Set correct workspace root
					setWorkspaceRootDirectory(rs);
					
					// Load and register source and target metamodels
					EPackage «srcProject.toLowerCase»Pack = null;
					EPackage «trgProject.toLowerCase»Pack = null;
					EPackage «projectName.toLowerCase»Pack = null;
					
					if(executable instanceof FWD_OPT) {
						Resource res = executable.getResourceHandler().loadResource("platform:/resource/«trgProject»/model/«trgProject».ecore");
						«trgProject.toLowerCase»Pack = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
						
						res = executable.getResourceHandler().loadResource("platform:/resource/«projectName»/model/«projectName».ecore");
						«projectName.toLowerCase»Pack = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
					}
							
					if(executable instanceof BWD_OPT) {
						Resource res = executable.getResourceHandler().loadResource("platform:/resource/«srcProject»/model/«srcProject».ecore");
						«srcProject.toLowerCase»Pack = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
						
						res = executable.getResourceHandler().loadResource("platform:/resource/«projectName»/model/«projectName».ecore");
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
					options.blackInterpreter(new HiPETGGEngine());
					options.project.name("«MoflonUtil.lastCapitalizedSegmentOf(projectName)»");
					options.project.path("«projectName»");
					options.debug.ibexDebug(false);
					options.csp.registerConstraintFactories(new RuntimeTGGAttrConstraintFactoryContainer().getFactories());
					options.registrationHelper(this);
					return options;
				}
			}
		'''
	}
}