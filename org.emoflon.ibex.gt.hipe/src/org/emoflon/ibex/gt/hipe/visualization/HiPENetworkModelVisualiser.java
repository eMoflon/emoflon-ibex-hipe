package org.emoflon.ibex.gt.hipe.visualization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.moflon.core.ui.VisualiserUtilities;
import org.moflon.core.ui.handler.visualisation.AbbreviateLabelsHandler;
import org.moflon.core.ui.handler.visualisation.ShowModelDetailsHandler;
import org.moflon.core.ui.visualisation.common.EMoflonEcoreVisualiser;
import org.moflon.core.ui.visualisation.diagrams.EdgeType;
import org.moflon.core.ui.visualisation.diagrams.VisualEdge;
import org.moflon.core.ui.visualisation.models.ObjectDiagram;
import org.moflon.core.ui.visualisation.models.ObjectDiagramStrategies;

import hipe.network.AbstractPort;
import hipe.network.ConstraintPort;
import hipe.network.MatchingNode;
import hipe.network.NetworkNode;
import hipe.network.ProductionNode;

/**
 * Visualises UML Object Diagrams for Ecore models.
 *
 */
public class HiPENetworkModelVisualiser extends EMoflonEcoreVisualiser<ObjectDiagram> {
	
	private Collection<VisualEdge> edges = new HashSet<>();
	private Collection<Object> handled = new HashSet<>();
	
	@Override
	public boolean supportsEditor(IEditorPart editor) {
		Collection<EObject> allElements = VisualiserUtilities.extractEcoreElements(editor);
		if(allElements != null && allElements.stream().anyMatch(e -> e instanceof MatchingNode || e instanceof ProductionNode))
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
		newSelection.addAll(selection.stream().filter(s -> s instanceof NetworkNode).collect(Collectors.toSet()));
		getNetworkSelection(newSelection);

		// Create diagram and process it using the defined strategy.
		ObjectDiagram diagram = strategy.apply(new ObjectDiagram(allObjects, newSelection));
//		diagram.setEdges(handleOpposites(diagram.getEdges()));
		diagram.setEdges(edges);
		diagram = determineObjectNames(diagram);
		diagram.setAbbreviateLabels(AbbreviateLabelsHandler.getVisPreference());
		diagram.setShowFullModelDetails(ShowModelDetailsHandler.getVisPreference());

		return HiPENetworkPlantUMLGenerator.visualiseModelElements(diagram);
	}
	
	private void getNetworkSelection(Collection<EObject> selection) {
		for(EObject obj : selection.stream().collect(Collectors.toSet())) {
			discover(selection, obj);
		}
	}

	private void discover(Collection<EObject> selection, EObject obj) {
		if(obj instanceof NetworkNode) {
			NetworkNode n = (NetworkNode) obj;
			String patternName = null;
			if(n instanceof ProductionNode)
				patternName = ((ProductionNode) n).getPatternName();
			if(n instanceof MatchingNode)
				patternName = ((MatchingNode) n).getPatternName();
			
			if(patternName != null && patternName.contains("_"))
				patternName = patternName.substring(0, patternName.lastIndexOf("_"));
				
			for(AbstractPort p : n.getInputPort()) {
				MatchingNode mNode = p.getParent().getMatchingnode();
				selection.add(mNode);
				
				if(patternName != null && mNode.getPatternName() != null && mNode.getPatternName().contains(patternName))
					discover(selection, mNode);

				if(!handled.contains(obj)) {
					String edgeName = p.getPortSlot().getName() + " ";
					if(p instanceof ConstraintPort) {
						ConstraintPort cP = (ConstraintPort) p;
						edgeName = cP.getPredicateCode();
					}
					VisualEdge edge = new VisualEdge(EdgeType.LINK, mNode, obj, edgeName);
					edges.add(edge);
				}
			}
		}
		handled.add(obj);
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
			if(current instanceof MatchingNode)
				labels.put(current, ((MatchingNode) current).getName());
			if(current instanceof ProductionNode)
				labels.put(current, ((ProductionNode) current).getName());
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
