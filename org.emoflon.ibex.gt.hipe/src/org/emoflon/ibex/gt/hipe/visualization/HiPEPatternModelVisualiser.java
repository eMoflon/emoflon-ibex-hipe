package org.emoflon.ibex.gt.hipe.visualization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.ui.IEditorPart;
import org.moflon.core.ui.VisualiserUtilities;
import org.moflon.core.ui.handler.visualisation.AbbreviateLabelsHandler;
import org.moflon.core.ui.handler.visualisation.ShowModelDetailsHandler;
import org.moflon.core.ui.visualisation.EMoflonPlantUMLGenerator;
import org.moflon.core.ui.visualisation.common.EMoflonEcoreVisualiser;
import org.moflon.core.ui.visualisation.diagrams.EdgeType;
import org.moflon.core.ui.visualisation.diagrams.VisualEdge;
import org.moflon.core.ui.visualisation.models.ObjectDiagram;
import org.moflon.core.ui.visualisation.models.ObjectDiagramStrategies;

import hipe.pattern.ComparatorType;
import hipe.pattern.ComplexConstraint;
import hipe.pattern.EqualConstraint;
import hipe.pattern.HiPEAbstractPattern;
import hipe.pattern.HiPEAttributeConstraint;
import hipe.pattern.HiPENodeConstraint;
import hipe.pattern.HiPEPartialPattern;
import hipe.pattern.HiPEPattern;
import hipe.pattern.RelationalConstraint;
import hipe.pattern.UnequalConstraint;

/**
 * Visualises UML Object Diagrams for Ecore models.
 *
 */
public class HiPEPatternModelVisualiser extends EMoflonEcoreVisualiser<ObjectDiagram> {
	
	private Collection<VisualEdge> edges = new HashSet<>();
	private Collection<Object> handled = new HashSet<>();
	
	@Override
	public boolean supportsEditor(IEditorPart editor) {
		Collection<EObject> allElements = VisualiserUtilities.extractEcoreElements(editor);
		if(allElements != null && allElements.stream().anyMatch(e -> e instanceof HiPEAbstractPattern))
			return true;
		
		return false;
	}
	
	@Override 
	public boolean supportsSelection(Collection<EObject> selection) {
		// An Ecore model must contain EObjects only, which are not EModelElements. If
		// it contains other
		// elements, the selection is not supported by this visualiser.
		return !VisualiserUtilities.hasMetamodelElements(selection);
	}

	@Override
	protected String getDiagramBody(Collection<EObject> selection) {
		Collection<EObject> allObjects = getAllElements();
		handled.clear();
		edges.clear();
		
		Collection<EObject> newSelection = new HashSet<>();
		newSelection.addAll(selection.stream().filter(s -> s instanceof HiPEAbstractPattern).collect(Collectors.toSet()));
		getNetworkSelection(newSelection);

		// Create diagram and process it using the defined strategy.
		ObjectDiagram diagram = strategy.apply(new ObjectDiagram(allObjects, newSelection));
//		diagram.setEdges(handleOpposites(diagram.getEdges()));
		diagram.setEdges(edges);
		diagram = determineObjectNames(diagram);
		diagram.setAbbreviateLabels(AbbreviateLabelsHandler.getVisPreference());
		diagram.setShowFullModelDetails(true);

		return HiPEPatternPlantUMLGenerator.visualiseModelElements(diagram);
	}
	
	private void getNetworkSelection(Collection<EObject> selection) {
		for(EObject obj : selection.stream().collect(Collectors.toSet())) {
			discover(selection, obj);
		}
	}

	private void discover(Collection<EObject> selection, EObject obj) {
		if(obj instanceof HiPEAbstractPattern) {
			HiPEAbstractPattern pattern = (HiPEAbstractPattern) obj;
			HiPEPattern rootPattern = null;
			if(pattern instanceof HiPEPattern)
				rootPattern = (HiPEPattern) pattern;
			if(pattern instanceof HiPEPartialPattern)
				rootPattern = ((HiPEPartialPattern) pattern).getRootPattern();
			
			for(HiPEPartialPattern p : pattern.getPartialPatterns()) {
				if(p.getNodes().size() == 1)
					continue;
				
				selection.add(p);
				
				if(p.getRootPattern().equals(rootPattern))
					discover(selection, p);

				if(!handled.contains(obj)) {
					String edgeName = "";
					for(HiPENodeConstraint nc : p.getNodeConstraints()) {
						if(nc instanceof EqualConstraint)
							edgeName += (edgeName.isEmpty() ? "" : "&&") + nc.getLeftNode().getName() + "==" + nc.getRightNode().getName();
						else
							if(nc instanceof UnequalConstraint)
								edgeName += (edgeName.isEmpty() ? "" : "&&") + nc.getLeftNode().getName() + "!=" + nc.getRightNode().getName();
							else throw new RuntimeException("Unknown node constraint detected!");

					}
					for(HiPEAttributeConstraint ac : p.getAttributeConstraints()) {
						if(ac instanceof RelationalConstraint) {
							RelationalConstraint rc = (RelationalConstraint) ac;
							String left = "";
							String right = "";
							if(rc.getLeftAttribute().getNode() != null) {
								left += rc.getLeftAttribute().getNode().getName() + "." + rc.getLeftAttribute().getName();
							}
							else {
								left += rc.getLeftAttribute().getValue();
							}
							if(rc.getRightAttribute().getNode() != null) {
								right += rc.getRightAttribute().getNode().getName() + "." + rc.getRightAttribute().getName();
							}
							else {
								right += rc.getRightAttribute().getValue();
							}
							edgeName += (edgeName.isEmpty() ? "" : "&&") + left + getStringComparator(rc.getType()) + right;
						}
						if(ac instanceof ComplexConstraint) {
							ComplexConstraint cc = (ComplexConstraint) ac;
							edgeName += (edgeName.isEmpty() ? "" : "&&") + cc.getPredicateCode(); 
						}
					}
					VisualEdge edge = new VisualEdge(EdgeType.LINK, p, obj, edgeName);
					edges.add(edge);
				}
			}
		}
		handled.add(obj);
	}
	
	public static String getStringComparator(ComparatorType comparator) {
		switch (comparator) {
		case EQUAL:
			return "==";
		case GREATER:
			return ">";
		case GREATER_OR_EQUAL:
			return ">=";
		case LESS:
			return "<";
		case LESS_OR_EQUAL:
			return "<=";
		case UNEQUAL:
			return "!=";
		}
		return null;
	}
	

	/**
	 * Determines instance names for all EObjects in selection and neighbourhood
	 * collection in the specified diagram.
	 * 
	 * @param diagram The diagram, for which the EObject instance names shall be
	 *                determined.
	 * @return The diagram with the EObject instance names.
	 */
	private ObjectDiagram determineObjectNames(ObjectDiagram diagram) {
		Map<EObject, String> labels = diagram.getInstanceNames();

		Collection<EObject> allObjects = new ArrayList<>();
		allObjects.addAll(diagram.getSelection());
		allObjects.addAll(diagram.getNeighbourhood());

		determineObjectNames(allObjects, labels);

		return diagram;
	}

	private void determineObjectNames(Collection<EObject> elements, Map<EObject, String> labels) {
		for (EObject current : elements) {
			if(current instanceof HiPEAbstractPattern)
				labels.put(current, ((HiPEAbstractPattern) current).getName());
//			labels.put(current, getLabel(current) + "_" + getIndex(current));
		}
	}

	@Override
	protected void chooseStrategy() {
		strategy = ObjectDiagramStrategies::determineEdgesForSelection;
//		if (NeighbourhoodStrategyHandler.getVisPreference()) {
//			strategy = strategy.andThen(ObjectDiagramStrategies::expandNeighbourhoodBidirectional);
//		}
	}
}
