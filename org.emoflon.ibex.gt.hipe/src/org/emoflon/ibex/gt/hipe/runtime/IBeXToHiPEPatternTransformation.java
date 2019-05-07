package org.emoflon.ibex.gt.hipe.runtime;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.EAttribute;

import IBeXLanguage.IBeXAttributeConstraint;
import IBeXLanguage.IBeXAttributeExpression;
import IBeXLanguage.IBeXAttributeParameter;
import IBeXLanguage.IBeXAttributeValue;
import IBeXLanguage.IBeXConstant;
import IBeXLanguage.IBeXContext;
import IBeXLanguage.IBeXContextAlternatives;
import IBeXLanguage.IBeXContextPattern;
import IBeXLanguage.IBeXEdge;
import IBeXLanguage.IBeXEnumLiteral;
import IBeXLanguage.IBeXNode;
import IBeXLanguage.IBeXNodePair;
import IBeXLanguage.IBeXPatternInvocation;
import IBeXLanguage.IBeXPatternSet;
import IBeXLanguage.IBeXRelation;
import hipe.pattern.ComparatorType;
import hipe.pattern.HiPEAbstractPattern;
import hipe.pattern.HiPEAttribute;
import hipe.pattern.HiPEAttributeConstraint;
import hipe.pattern.HiPEEdge;
import hipe.pattern.HiPENode;
import hipe.pattern.HiPEPartialPattern;
import hipe.pattern.HiPEPattern;
import hipe.pattern.HiPEPatternContainer;
import hipe.pattern.HiPEPatternFactory;
import hipe.pattern.HiPEPatternInvocation;
import hipe.pattern.RelationalConstraint;
import hipe.pattern.UnequalConstraint;

public class IBeXToHiPEPatternTransformation {

	private HiPEPatternFactory factory;
	
	protected Map<String, HiPEPattern> name2pattern = new HashMap<>();
	protected Map<IBeXNode, HiPENode> node2node = new HashMap<>();
	
	public HiPEPatternContainer transform(IBeXPatternSet patternSet) {
		factory = HiPEPatternFactory.eINSTANCE;
		
		name2pattern = new HashMap<>();
		node2node = new HashMap<>();
		
		HiPEPatternContainer container = factory.createHiPEPatternContainer();
		for(IBeXContext context : patternSet.getContextPatterns()) {
			if(context instanceof IBeXContextPattern) {
				IBeXContextPattern pattern = (IBeXContextPattern) context;
				if(pattern.getSignatureNodes().isEmpty())
					continue;
				
				container.getPatterns().add(transform(pattern));
			}
			if(context instanceof IBeXContextAlternatives)
				for(IBeXContextPattern alternative : ((IBeXContextAlternatives) context).getAlternativePatterns()) {
					if(alternative.getSignatureNodes().isEmpty()) {
						continue;
					}
					
					container.getPatterns().add(transform(alternative));
				}
		}
		
		return container;
	}

	public HiPEPattern transform(IBeXContextPattern context) {
		context.setName(context.getName().replace("-", "_"));
		
		if(name2pattern.containsKey(context.getName()))
			return name2pattern.get(context.getName());
		
		HiPEPattern pattern = factory.createHiPEPattern();
		pattern.setName(context.getName());
		
		name2pattern.put(pattern.getName(), pattern);
		
		for(IBeXPatternInvocation inv : context.getInvocations()) {
			HiPEPatternInvocation invocation = factory.createHiPEPatternInvocation();
			
			HiPEPattern invoked = transform(inv.getInvokedPattern());
			invocation.setInvokedPattern(invoked);
			invocation.setPositive(inv.isPositive());
			pattern.getPatternInvocations().add(invocation);
			
			EMap<IBeXNode, IBeXNode> mapping = inv.getMapping();
			for(IBeXNode node : mapping.keySet()) {
				HiPENode srcNode = transform(node);
				HiPENode trgNode = transform(mapping.get(node));
				trgNode.getInvokedBy().add(srcNode);
			}
		}
		
		for(IBeXNode node : context.getSignatureNodes()) {
			pattern.getSignatureNodes().add(transform(node));
		}
		
		for(IBeXNode node : context.getLocalNodes()) {
			pattern.getSignatureNodes().add(transform(node));
		}
		
		for(IBeXEdge edge : context.getLocalEdges()) {
			pattern.getEdges().add(transform(edge));
		}
		
		for(IBeXNodePair injectivity : context.getInjectivityConstraints()) {
			pattern.getNodeConstraints().add(transform(injectivity));
		}
		
		for(IBeXAttributeConstraint constr : context.getAttributeConstraint()) {
			HiPEAttributeConstraint constraint = transform(pattern, constr);
			if(constraint != null)
				pattern.getAttributeConstraints().add(constraint);
		}
		
		return pattern;
	}
	
	public HiPENode transform(IBeXNode node) {
		if(node2node.containsKey(node))
			return node2node.get(node);
		
		HiPENode hNode = factory.createHiPENode();
		hNode.setName(node.getName());
		hNode.setType(node.getType());
		
		node2node.put(node, hNode);
		
		return hNode;
	}
	
	private HiPEEdge transform(IBeXEdge edge) {
		HiPEEdge hEdge = factory.createHiPEEdge();
		hEdge.setName(edge.getSourceNode().getType().getName() + "_" + edge.getType().getName());
		hEdge.setType(edge.getType());
		hEdge.setSource(transform(edge.getSourceNode()));
		hEdge.setTarget(transform(edge.getTargetNode()));
		return hEdge;
	}
	
	private UnequalConstraint transform(IBeXNodePair pair) {
		UnequalConstraint constr = factory.createUnequalConstraint();
		constr.setLeftNode(transform(pair.getValues().get(0)));
		constr.setRightNode(transform(pair.getValues().get(1)));
		return constr;
	}
	
	private HiPEAttributeConstraint transform(HiPEPattern pattern, IBeXAttributeConstraint constr) {
		RelationalConstraint rConstraint = factory.createRelationalConstraint();
		rConstraint.setLeftAttribute(transform(constr.getNode(), constr.getType()));
		rConstraint.setRightAttribute(transform(constr.getValue()));
		rConstraint.setType(transform(constr.getRelation()));

		if(rConstraint.getLeftAttribute() == null || rConstraint.getRightAttribute() == null)
			return null;

		pattern.getAttributes().add(rConstraint.getLeftAttribute());
		pattern.getAttributes().add(rConstraint.getRightAttribute());
		
		
		return rConstraint;
	}
	
	private HiPEAttribute transform(IBeXAttributeValue value) {
		if(value instanceof IBeXConstant)
			return transform((IBeXConstant) value);
		if(value instanceof IBeXEnumLiteral)
			return transform((IBeXEnumLiteral) value);
		if(value instanceof IBeXAttributeParameter)
			// TODO: implement attribute parameter
//			return transform((IBeXAttributeParameter) value);
			return null;
		if(value instanceof IBeXAttributeExpression)
			return transform((IBeXAttributeExpression) value);
		return null;
	}
	
	private HiPEAttribute transform(IBeXConstant constant) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		attr.setValue(constant.getValue());
		return attr;
	}
	
	private HiPEAttribute transform(IBeXEnumLiteral literal) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		attr.setValue(literal.getLiteral());
		return attr;
	}
	
	private HiPEAttribute transform(IBeXAttributeParameter attributeParam) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		attr.setName(attributeParam.getName());
		return attr;
	}
	
	private HiPEAttribute transform(IBeXAttributeExpression attributeExpr) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		attr.setNode(transform(attributeExpr.getNode()));
		attr.setValue(attributeExpr.getAttribute());
		return attr;
	}

	private ComparatorType transform(IBeXRelation relation) {
		switch(relation) {
		case EQUAL: return ComparatorType.EQUAL;
		case GREATER: return ComparatorType.GREATER;
		case GREATER_OR_EQUAL: return ComparatorType.GREATER_OR_EQUAL;
		case SMALLER: return ComparatorType.LESS;
		case SMALLER_OR_EQUAL: return ComparatorType.LESS_OR_EQUAL;
		case UNEQUAL: return ComparatorType.UNEQUAL;
		}
		return null;
	}

	private HiPEAttribute transform(IBeXNode iBeXNode, EAttribute attr) {
		HiPEAttribute hAttr = factory.createHiPEAttribute();
		hAttr.setName(attr.getName());
		hAttr.setValue(attr);
		hAttr.setNode(transform(iBeXNode));
		
		return hAttr;
	}

	
}
