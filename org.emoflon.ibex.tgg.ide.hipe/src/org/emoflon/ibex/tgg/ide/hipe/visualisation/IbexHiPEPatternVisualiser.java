package org.emoflon.ibex.tgg.ide.hipe.visualisation;

import hipe.pattern.HiPEPattern;
import java.util.Optional;

import org.eclipse.emf.ecore.presentation.EcoreEditor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.IEditorPart;
import org.moflon.core.ui.visualisation.EMoflonPlantUMLGenerator;
import org.moflon.core.ui.visualisation.common.EMoflonVisualiser;

public class IbexHiPEPatternVisualiser extends EMoflonVisualiser {

	@Override
	protected String getDiagramBody(IEditorPart editor, ISelection selection) {
		return maybeVisualisePattern(editor, selection).orElse(
			   EMoflonPlantUMLGenerator.emptyDiagram());
	}
	
	private Optional<Object> selectionInEcoreEditor(IEditorPart editor){
		return Optional.of(editor)
				.flatMap(maybeCast(EcoreEditor.class))
				.map(EcoreEditor::getSelection)
				.flatMap(maybeCast(TreeSelection.class))
				.map(TreeSelection::getFirstElement);
	}
	
	private Optional<String> maybeVisualisePattern(IEditorPart editor, ISelection selection) {
		return extractPatternsFromEditor(editor)					
				.map(pb -> IbexHiPEPlantUMLGenerator.visualisePatternBody(pb, "0" + IbexHiPEPlantUMLGenerator.separator()));
	}

	private Optional<HiPEPattern> extractPatternsFromEditor(IEditorPart editor) {
		return Optional.of(editor)
				.flatMap(this::selectionInEcoreEditor)
				.flatMap(maybeCast(HiPEPattern.class));
	}
	
	@Override
	public boolean supportsEditor(IEditorPart editor) {
		return extractPatternsFromEditor(editor).isPresent();
	}

}
