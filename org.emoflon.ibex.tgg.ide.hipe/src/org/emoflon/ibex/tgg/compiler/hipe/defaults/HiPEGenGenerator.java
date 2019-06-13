package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.codegen.ecore.CodeGenEcorePlugin;
import org.eclipse.emf.codegen.ecore.generator.Generator;
import org.eclipse.emf.codegen.ecore.generator.GeneratorAdapter;
import org.eclipse.emf.codegen.ecore.genmodel.GenClass;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.util.CodeGenUtil;
import org.eclipse.emf.common.CommonPlugin;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.Monitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.plugin.EcorePlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;

public class HiPEGenGenerator extends Generator {

	private static final boolean SYSOUT_BEGIN_END = false;

	public Diagnostic generate(Object object, Object projectType, String projectTypeName, Monitor monitor, String projectName) {
		if (SYSOUT_BEGIN_END)
			System.out.println("******* Begin: " + new java.util.Date());
		try {
			String message = projectTypeName != null
					? CodeGenEcorePlugin.INSTANCE.getString("_UI_Generating_message", new Object[] { projectTypeName })
					: CodeGenEcorePlugin.INSTANCE.getString("_UI_GeneratingCode_message");
			BasicDiagnostic result = new BasicDiagnostic(CodeGenEcorePlugin.ID, 0, message, null);

			GeneratorData[] data = getGeneratorData(object, projectType, true);
			monitor.beginTask("", data.length + 2);
			monitor.subTask(message);

			// Initialization is deferred until adapters are attached to all the objects of
			// interest and we're
			// about to ask them to generate.
			//
			if (initializeNeeded) {
				initializeNeeded = false;
				initialize();
			}

			// Give all generator adapters the chance to do setup work.
			//
			int preIndex = 0;
			for (; preIndex < data.length && canContinue(result); preIndex++) {
				if(ignoreGeneratorData(data[preIndex], projectName))
					continue;
					
				result.add(data[preIndex].adapter.preGenerate(data[preIndex].object, projectType));
			}
			monitor.worked(1);

			// Invoke generator adapters for each object.
			//
			for (int i = 0; i < data.length && canContinue(result); i++) {
				if(ignoreGeneratorData(data[i], projectName))
					continue;

				result.add(
						data[i].adapter.generate(data[i].object, projectType, CodeGenUtil.createMonitor(monitor, 1)));
				if (monitor.isCanceled()) {
					result.add(Diagnostic.CANCEL_INSTANCE);
				}
			}

			// Give all generator adapters the chance to do tear down.
			//
			for (int i = 0; i < preIndex; i++) {
				if(ignoreGeneratorData(data[i], projectName))
					continue;
				
				result.add(data[i].adapter.postGenerate(data[i].object, projectType));
			}

			// Optionally invoke any source cleanup actions.
			// This is only possible if JDT and JDT UI are available.
			//
			if (getOptions().cleanup && CommonPlugin.IS_RESOURCES_BUNDLE_AVAILABLE && !generatedOutputs.isEmpty()
					&& jControlModel != null && jControlModel.getFacadeHelper() != null) {
				EclipseHelper.sourceCleanup(generatedOutputs);
			}

			return result;
		} finally {
			monitor.done();
			if (SYSOUT_BEGIN_END)
				System.out.println("******* End: " + new java.util.Date());
		}
	}
	
	private boolean ignoreGeneratorData(GeneratorData data, String projectName) {
		if(data.object instanceof GenPackage) {
			GenPackage gen = (GenPackage) data.object;
			return !gen.getNSName().contains(projectName);
		}
		if(data.object instanceof GenClass) {
			GenClass gen = (GenClass) data.object;
			return !gen.getGenPackage().getNSName().contains(projectName);
		}
		
		return false;
	}
	
	private GeneratorData[] getGeneratorData(Object object, Object projectType, boolean forGenerate)
	  {
	    // Since we're invoking plugged-in code, we must be defensive against cycles.
	    //
	    Set<Object> objects = new HashSet<Object>();

	    // Compute the GeneratorData for the given object and its children, then for the parents of the given object.
	    //

	    List<GeneratorData> childrenData = getGeneratorData(object, projectType, forGenerate, true, false, objects);
	    List<GeneratorData> parentsData = getGeneratorData(object, projectType, forGenerate, false, true, objects);

	    // Combine the two lists.
	    //
	    List<GeneratorData> result = new ArrayList<GeneratorData>(parentsData.size() + childrenData.size());
	    Collections.reverse(parentsData);
	    result.addAll(parentsData);
	    result.addAll(childrenData);
	    return result.toArray(new GeneratorData[result.size()]);
	  }

	  private List<GeneratorData> getGeneratorData(Object object, Object projectType, boolean forGenerate, boolean forChildren, boolean skipFirst, Set<Object> objects)
	  {
	    List<Object> result  = new ArrayList<Object>();
	    result.add(object);

	    for (int i = 0; i < result.size(); skipFirst = false)
	    {
	      Object o = result.get(i);

	      Collection<GeneratorAdapter> adapters = getAdapters(o);
	      result.remove(i);
	      if (!adapters.isEmpty())
	      {
	        for (GeneratorAdapter adapter : adapters)
	        {
	          if (forChildren)
	          {
	            Collection<?> children = forGenerate ? adapter.getGenerateChildren(o, projectType) : adapter.getCanGenerateChildren(o, projectType);
	            for (Object child : children)
	            {
	              if (objects.add(child))
	              {
	                result.add(child);
	              }
	            }
	          }
	          else
	          {
	            Object parent = forGenerate ? adapter.getGenerateParent(o, projectType) : adapter.getCanGenerateParent(o, projectType);
	            if (parent != null && objects.add(parent))
	            {
	              result.add(parent);
	            }
	          }

	          if (!skipFirst)
	          {
	            result.add(i++, new GeneratorData(o, adapter));
	          }
	        }
	      }
	    }
	    
	    @SuppressWarnings("unchecked")
	    List<GeneratorData> list = (List<GeneratorData>)(List<?>)result;
	    return list;
	  }

	private static class GeneratorData {
		public Object object;
		public GeneratorAdapter adapter;

		public GeneratorData(Object object, GeneratorAdapter adapter) {
			this.object = object;
			this.adapter = adapter;
		}
	}

	private static class EclipseHelper {
		private static final CleanupScheduler SCHEDULER;
		static {
			CleanupScheduler cleanupScheduler = null;
			try {
				Class<?> generatorUIUtilClass = CommonPlugin.loadClass("org.eclipse.emf.codegen.ecore.ui",
						"org.eclipse.emf.codegen.ecore.genmodel.presentation.GeneratorUIUtil");
				cleanupScheduler = (CleanupScheduler) generatorUIUtilClass.getField("CLEANUP_SCHEDULER").get(null);
			} catch (Exception exception) {
				// Ignore
			}

			SCHEDULER = cleanupScheduler;
		}

		public static void sourceCleanup(final Set<URI> generatedOutputs) {
			if (SCHEDULER != null) {
				IWorkspaceRoot workspaceRoot = EcorePlugin.getWorkspaceRoot();
				if (workspaceRoot != null) {
					Set<ICompilationUnit> compilationUnits = new LinkedHashSet<ICompilationUnit>();
					for (URI generatedOutput : generatedOutputs) {
						if ("java".equals(generatedOutput.fileExtension())) {
							IFile file = workspaceRoot.getFile(new Path(generatedOutput.toString()));
							ICompilationUnit compilationUnit = JavaCore.createCompilationUnitFrom(file);
							if (compilationUnit.getJavaProject().isOnClasspath(compilationUnit)) {
								compilationUnits.add(compilationUnit);
							}
						}
					}

					SCHEDULER.schedule(compilationUnits);
				}
			}
		}
	}
}
