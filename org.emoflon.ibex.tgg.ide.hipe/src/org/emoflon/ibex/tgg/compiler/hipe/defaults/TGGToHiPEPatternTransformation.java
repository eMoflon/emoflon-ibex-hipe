package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXAttributeValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXEdge;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXEnumValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXMatchCountValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXNode;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXPattern;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXPatternInvocation;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXStringValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.BooleanExpression;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.RelationalExpression;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.RelationalOperator;
import org.emoflon.ibex.gt.build.hipe.IBeXToHiPEPatternTransformation;
import org.emoflon.ibex.gt.gtmodel.IBeXGTModel.GTPattern;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.OperationalisationMode;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.TGGModel;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.TGGOperationalRule;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.TGGPattern;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.TGGRule;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.CSP.TGGAttributeConstraint;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.CSP.TGGAttributeConstraintParameterValue;

import hipe.pattern.ComplexConstraint;
import hipe.pattern.HiPEAttribute;
import hipe.pattern.HiPEAttributeConstraint;
import hipe.pattern.HiPEContainer;
import hipe.pattern.HiPECountInvocation;
import hipe.pattern.HiPENode;
import hipe.pattern.HiPEPattern;
import hipe.pattern.HiPEPatternFactory;
import hipe.pattern.HiPEPatternInvocation;

public class TGGToHiPEPatternTransformation extends IBeXToHiPEPatternTransformation {
	private int csp_id = 0;
	
	
	public HiPEContainer transform(TGGModel model, OperationalisationMode... modes) {
		var allModes = new HashSet<>();
		for(var mode : modes) 
			allModes.add(mode);
			
		
		factory = HiPEPatternFactory.eINSTANCE;
		this.model = model;
		name2pattern = new HashMap<>();
		node2node = new HashMap<>();

		container = factory.createHiPEContainer();
		for (TGGRule rule : model.getRuleSet().getRules()) {
			for(TGGOperationalRule operationalRule : rule.getOperationalisations()) {
				var pattern = operationalRule.getPrecondition();
				
				// do not transform if the right mode has not been given as a parameter
				if(!allModes.contains(operationalRule.getOperationalisationMode()))
					continue;
					
				// if the pattern is empty, then do not generate anything
				if (pattern.getSignatureNodes().isEmpty() && pattern.getLocalNodes().isEmpty())
					continue;
				
				switch(operationalRule.getOperationalisationMode()) {
					case FORWARD:
						if(rule.getCreateSource().getNodes().isEmpty() && rule.getCreateSource().getEdges().isEmpty())
							continue;
						break;
					case BACKWARD:
						if(rule.getCreateTarget().getNodes().isEmpty() && rule.getCreateTarget().getEdges().isEmpty())
							continue;
						break;
					default:
				}
				
				container.getPatterns().add(transform((TGGPattern) pattern));
			}
		}

		// Finish count Invocations
		for (HiPECountInvocation hipeCount : hipeCount2Count.keySet()) {
			IBeXMatchCountValue ibexCount = hipeCount2Count.get(hipeCount);

			hipeCount.setInvokedPattern(ibexPattern2pattern.get(ibexCount.getInvocation().getInvocation()));

			EMap<IBeXNode, IBeXNode> mapping = ibexCount.getInvocation().getMapping();
			for (IBeXNode node : mapping.keySet()) {
				HiPENode srcNode = node2node.get(node);
				HiPENode trgNode = node2node.get(mapping.get(node));
				hipeCount.getInvocationNodeMap().put(srcNode, trgNode);
			}
		}

		return container;
	}
	
	private HiPEPattern transform(IBeXPattern context) {
		context.setName(context.getName().replace("-", "_"));

		if (name2pattern.containsKey(context.getName()))
			return name2pattern.get(context.getName());

		HiPEPattern pattern = factory.createHiPEPattern();
		pattern.setName(context.getName());
		
		container.getPatterns().add(pattern);
		name2pattern.put(pattern.getName(), pattern);

		for (IBeXPatternInvocation inv : context.getInvocations()) {
			HiPEPatternInvocation invocation = factory.createHiPEPatternInvocation();

			HiPEPattern invoked = transform((IBeXPattern) inv.getInvocation());
			invocation.setInvokedPattern(invoked);
			invocation.setPositive(inv.isPositive());
			pattern.getPatternInvocations().add(invocation);

			EMap<IBeXNode, IBeXNode> mapping = inv.getMapping();
			for (IBeXNode node : mapping.keySet()) {
				HiPENode srcNode = transform(context, node);
				HiPENode trgNode = transform(context, mapping.get(node));
				invocation.getInvocationNodeMap().put(srcNode, trgNode);
			}
		}

		for (IBeXNode node : context.getSignatureNodes()) {
			pattern.getNodes().add(transform(context, node));
		}

		for (IBeXNode node : context.getLocalNodes()) {
			pattern.getNodes().add(transform(context, node));
		}

		for (IBeXEdge edge : context.getEdges()) {
			pattern.getEdges().add(transform(context, edge));
		}

		Set<BooleanExpression> transformed = new HashSet<>();
		// Filter simple node constraints and transform to hipe node constraints
		for (RelationalExpression nodeConstraint : context.getConditions().stream()
				.filter(expr -> isSimpleNodeConstraint(expr)).map(expr -> (RelationalExpression) expr)
				.collect(Collectors.toList())) {
			if (nodeConstraint.getOperator() == RelationalOperator.OBJECT_EQUALS) {
				pattern.getNodeConstraints().add(transformE(context, nodeConstraint));
			} else {
				pattern.getNodeConstraints().add(transformUE(context, nodeConstraint));
			}
			transformed.add(nodeConstraint);
		}

		// Filter simple attribute constraints and transform to hipe attribute
		// constraints
		for (RelationalExpression constr : context.getConditions().stream()
				.filter(expr -> isSimpleAttributeConstraint(expr)).map(expr -> (RelationalExpression) expr)
				.collect(Collectors.toList())) {
			HiPEAttributeConstraint constraint = transformSimpleAC(context, pattern, constr);

			if (constraint != null)
				pattern.getAttributeConstraints().add(constraint);

			transformed.add(constr);
		}

		if(context instanceof TGGPattern tggPattern) {
			for(var csp : tggPattern.getAttributeConstraints().getTggAttributeConstraints()) {
				pattern.getAttributeConstraints().add(transform(context, pattern, csp));
			}			
		}

		ibexPattern2pattern.put(context, pattern);
		return pattern;
	}

	private HiPEAttributeConstraint transform(IBeXPattern ibexPattern, HiPEPattern hipePattern, TGGAttributeConstraint csp) {
		ComplexConstraint cConstraint = factory.createComplexConstraint();
		container.getAttributeConstraints().add(cConstraint);
		var definition = csp.getDefinition();
		
		String initCode = definition.getLibrary().getPackageName() + "." + getCSPName(definition.getName()) + " csp_" + csp_id + " = new " + definition.getLibrary().getPackageName() + "." + getCSPName(csp.getDefinition().getName()) + "();\n";
		for(TGGAttributeConstraintParameterValue value : csp.getParameters()) {
			initCode += "csp_" + csp_id + ".getVariables().add(new org.emoflon.ibex.tgg.runtime.csp.RuntimeTGGAttributeConstraintVariable(true, ";
			if(value.getExpression() instanceof IBeXAttributeValue attributeValue) {
				String getOrIs = ".get";
				if(attributeValue.getAttribute().getEType().getInstanceClassName() != null) {
					getOrIs = attributeValue.getAttribute().getEType().getInstanceClassName().equals("boolean") ? ".is" : ".get";
				}
				initCode += attributeValue.getNode().getName() + getOrIs + attributeValue.getAttribute().getName().substring(0, 1).toUpperCase() + attributeValue.getAttribute().getName().substring(1) + "()";
				HiPEAttribute hAttr = transformSimple(ibexPattern, attributeValue);
				cConstraint.getAttributes().add(hAttr);
				hipePattern.getAttributes().add(hAttr);
				if(attributeValue.getAttribute().getEType().getInstanceClassName() != null) {
					initCode += ", \"" + attributeValue.getAttribute().getEType().getInstanceClassName() + "\"));\n";
				} else {
					initCode += ", \"Enum::" + attributeValue.getAttribute().getEType().getName() + "\"));\n";
				}
			} else
			if(value.getExpression() instanceof IBeXEnumValue literal) {
				EEnum eenum = literal.getLiteral().getEEnum();
				
				initCode += literal.getType().getEPackage().getNsPrefix() + "." + eenum.getName() + "." + literal.getLiteral().getName();//.replaceAll("\"\"", "\"");
				HiPEAttribute hAttr = transformSimple(ibexPattern, literal);
				cConstraint.getAttributes().add(hAttr);
				hipePattern.getAttributes().add(hAttr);
				initCode += ", \"" + literal.getLiteral().getClass().getName() + "\"));\n";
			} else {
				HiPEAttribute hAttr = transformSimple(ibexPattern, value.getExpression());
				if(value.getExpression() instanceof IBeXStringValue) 
					initCode += "\""+hAttr.getValue()+"\"";
				else
					initCode += hAttr.getValue();
				cConstraint.getAttributes().add(hAttr);
				hipePattern.getAttributes().add(hAttr);
				initCode += ", \"" + hAttr.getValue().getClass().getName() + "\"));\n";
			}
		}
		initCode += "csp_" + csp_id + ".solve();\n";
		
		cConstraint.setInitializationCode(initCode);
		cConstraint.setPredicateCode("csp_" + csp_id + ".isSatisfied()");
		
		csp_id++;
		return cConstraint;
	}
	
	private String getCSPName(String name) {
		if(name.startsWith("eq_"))
			return "Eq";
		
		return name.substring(0, 1).toUpperCase() + name.substring(1);
	}
}
