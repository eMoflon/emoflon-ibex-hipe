package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import org.eclipse.emf.codegen.ecore.CodeGenEcorePlugin;
import org.eclipse.emf.codegen.ecore.generator.GeneratorAdapterFactory.Descriptor;
import org.eclipse.emf.codegen.ecore.genmodel.GenClass;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.util.CodeGenUtil;
import org.eclipse.emf.common.CommonPlugin;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.Monitor;
import org.moflon.emf.codegen.CodeGenerator;

public class HiPEGenGenerator extends CodeGenerator {
	
	public HiPEGenGenerator() {
		super(null);
	}

	public HiPEGenGenerator(Descriptor descriptor) {
		super(descriptor);
	}
	
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

	protected boolean ignoreGeneratorData(GeneratorData data, String projectName) {
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
	
}
