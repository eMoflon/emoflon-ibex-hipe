package org.emoflon.ibex.gt.hipe.runtime;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EEnum;
import org.emoflon.ibex.common.patterns.IBeXPatternFactory;
import org.emoflon.ibex.gt.hipe.ide.codegen.GTHiPEBuilderExtension;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXArithmeticAttribute;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXArithmeticExpression;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXArithmeticValue;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXArithmeticValueLiteral;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXAttributeConstraint;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXAttributeExpression;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXAttributeParameter;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXAttributeValue;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXBinaryExpression;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXCSP;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXConstant;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXContext;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXContextAlternatives;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXContextPattern;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXEdge;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXEnumLiteral;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXInjectivityConstraint;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXNode;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXPatternInvocation;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXPatternModelFactory;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXPatternSet;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXRelation;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXStochasticAttributeValue;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXUnaryExpression;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.impl.IBeXPatternModelFactoryImpl;
import org.moflon.core.utilities.LogUtils;

import hipe.pattern.ComparatorType;
import hipe.pattern.ComplexConstraint;
import hipe.pattern.HiPEAttribute;
import hipe.pattern.HiPEAttributeConstraint;
import hipe.pattern.HiPEEdge;
import hipe.pattern.HiPENode;
import hipe.pattern.HiPEPattern;
import hipe.pattern.HiPEContainer;
import hipe.pattern.HiPEPatternFactory;
import hipe.pattern.HiPEPatternInvocation;
import hipe.pattern.RelationalConstraint;
import hipe.pattern.UnequalConstraint;

public class IBeXToHiPEPatternTransformation {
	
	private static Logger logger = Logger.getLogger(GTHiPEBuilderExtension.class);

	private HiPEPatternFactory factory;
	
	protected Map<String, HiPEPattern> name2pattern = new HashMap<>();
	protected Map<IBeXNode, HiPENode> node2node = new HashMap<>();
	protected HiPEContainer container;
	
	private int csp_id = 0;
	
	public HiPEContainer transform(IBeXPatternSet patternSet) {
		factory = HiPEPatternFactory.eINSTANCE;
		
		name2pattern = new HashMap<>();
		node2node = new HashMap<>();
		
		container = factory.createHiPEContainer();
		for(IBeXContext context : patternSet.getContextPatterns()) {
			if(context instanceof IBeXContextPattern) {
				IBeXContextPattern pattern = (IBeXContextPattern) context;
				if(pattern.getSignatureNodes().isEmpty() && pattern.getLocalNodes().isEmpty())
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
				HiPENode srcNode = transform(context, node);
				HiPENode trgNode = transform(context, mapping.get(node));
				invocation.getInvocationNodeMap().put(srcNode, trgNode);
			}
		}
		
		for(IBeXNode node : context.getSignatureNodes()) {
			pattern.getNodes().add(transform(context, node));
		}
		
		for(IBeXNode node : context.getLocalNodes()) {
			pattern.getNodes().add(transform(context, node));
		}
		
		for(IBeXEdge edge : context.getLocalEdges()) {
			pattern.getEdges().add(transform(context,edge));
		}
		
		for(IBeXInjectivityConstraint injectivity : context.getInjectivityConstraints()) {
			pattern.getNodeConstraints().add(transform(context,injectivity));
		}
		
		for(IBeXAttributeConstraint constr : context.getAttributeConstraint()) {
			HiPEAttributeConstraint constraint = null;
			if(isSimpleAttributeValue(constr.getLhs()) && isSimpleAttributeValue(constr.getRhs())) {
				constraint = transformSimpleAC(context, pattern, constr);
			} else {
				constraint = transformComplexAC(context, pattern, constr);
			}
	
			 
			if(constraint != null)
				pattern.getAttributeConstraints().add(constraint);
		}
		
		for(IBeXCSP csp : context.getCsps()) {
			pattern.getAttributeConstraints().add(transform(context, pattern, csp));
		}
		
		return pattern;
	}
	
	public boolean isSimpleAttributeValue(IBeXAttributeValue value) {
		if(value instanceof IBeXConstant)
			return true;
		if(value instanceof IBeXEnumLiteral)
			return true;
		if(value instanceof IBeXAttributeParameter)
			return true;
		if(value instanceof IBeXAttributeExpression)
			return true;
		
		return false;
	}
	
	public HiPENode transform(IBeXContextPattern context, IBeXNode node) {
		if(node2node.containsKey(node))
			return node2node.get(node);
		HiPENode hNode = factory.createHiPENode();
		container.getNodes().add(hNode);
		hNode.setName(node.getName());
		hNode.setType(node.getType());
		hNode.setLocal(context.getLocalNodes().contains(node));
		
		node2node.put(node, hNode);
		
		return hNode;
	}
	
	private HiPEEdge transform(IBeXContextPattern context, IBeXEdge edge) {
		HiPEEdge hEdge = factory.createHiPEEdge();
		container.getEdges().add(hEdge);
		hEdge.setName(edge.getSourceNode().getType().getName() + "_" + edge.getType().getName());
		hEdge.setType(edge.getType());
		hEdge.setSource(transform(context, edge.getSourceNode()));
		hEdge.setTarget(transform(context, edge.getTargetNode()));
		return hEdge;
	}
	
	private UnequalConstraint transform(IBeXContextPattern context, IBeXInjectivityConstraint pair) {
		UnequalConstraint constr = factory.createUnequalConstraint();
		container.getNodeConstraints().add(constr);
		constr.setLeftNode(transform(context, pair.getValues().get(0)));
		constr.setRightNode(transform(context, pair.getValues().get(1)));
		return constr;
	}
	
	private HiPEAttributeConstraint transformSimpleAC(IBeXContextPattern context, HiPEPattern pattern, IBeXAttributeConstraint constr) {
		RelationalConstraint rConstraint = factory.createRelationalConstraint();
		HiPEAttribute attrLeft = transform(context, constr.getLhs());
		HiPEAttribute attrRight = transform(context, constr.getRhs());
		if(attrLeft != null) {
			pattern.getAttributes().add(attrLeft);
			rConstraint.setLeftAttribute(attrLeft);
		}
		if(attrRight != null) {
			pattern.getAttributes().add(attrRight);
			rConstraint.setRightAttribute(attrRight);
		}
		rConstraint.setType(transform(context, constr.getRelation()));
		
		if(attrLeft == null || attrRight == null)
			return null;
		
		container.getAttributeConstraints().add(rConstraint);

		pattern.getAttributes().add(rConstraint.getLeftAttribute());
		pattern.getAttributes().add(rConstraint.getRightAttribute());
		
		return rConstraint;
	}
	
	private HiPEAttributeConstraint transformComplexAC(IBeXContextPattern context, HiPEPattern pattern, IBeXAttributeConstraint constr) {
		ComplexConstraint cConstraint = factory.createComplexConstraint();
		Collection<HiPEAttribute> attributes = new HashSet<>();
		String leftExpr = null;
		String rightExpr = null;
		try {
			leftExpr = transformAttributeValue2Java(context, constr.getLhs(), attributes);
		} catch(UnsupportedOperationException e) {
			LogUtils.error(logger, e.getMessage());
			return null;
		}
		try {
			rightExpr = transformAttributeValue2Java(context, constr.getRhs(), attributes);
		} catch(UnsupportedOperationException e) {
			LogUtils.error(logger, e.getMessage());	
			return null;
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append(leftExpr);
		switch(constr.getRelation()) {
		case EQUAL:
			sb.append(" == ");
			break;
		case GREATER:
			sb.append(" > ");
			break;
		case GREATER_OR_EQUAL:
			sb.append(" >= ");
			break;
		case SMALLER:
			sb.append(" < ");
			break;
		case SMALLER_OR_EQUAL:
			sb.append(" <= ");
			break;
		case UNEQUAL:
			sb.append(" != ");
			break;
		default:
			LogUtils.error(logger, "Unknown relational operator -> constraint will be ignored..");	
			return null;
		}
		sb.append(rightExpr);
		cConstraint.getAttributes().addAll(attributes);
		cConstraint.setInitializationCode("");
		cConstraint.setPredicateCode(sb.toString());
		
		container.getAttributeConstraints().add(cConstraint);
		return cConstraint;
	}
	
	private String transformAttributeValue2Java(IBeXContextPattern context, IBeXAttributeValue value, Collection<HiPEAttribute> attributes) throws UnsupportedOperationException{
		if(value instanceof IBeXConstant)
			return transformAttributeValue2Java(context, (IBeXConstant) value, attributes);
		else if(value instanceof IBeXEnumLiteral) // Enum literals do not make sense in attribute constraints with arithmetic expressions
			throw new UnsupportedOperationException("IBeXEnumLiteral not supported in complex attribute constraints -> constraint will be ignored..");	
		else if(value instanceof IBeXAttributeParameter) // TODO: implement attribute parameter
			throw new UnsupportedOperationException("IBeXAttributeParameter not yet supported -> constraint will be ignored..");
		else if(value instanceof IBeXStochasticAttributeValue) // TODO: implement attribute parameter
			throw new UnsupportedOperationException("IBeXStochasticAttributeValue not yet supported -> constraint will be ignored..");
		else if(value instanceof IBeXAttributeExpression)
			return transformAttributeValue2Java(context, (IBeXAttributeExpression) value, attributes);
		else if(value instanceof IBeXArithmeticValue)
			return transformAttributeValue2Java(context, ((IBeXArithmeticValue) value).getExpression(), attributes);
		else
			throw new UnsupportedOperationException("Unknown attribute expression type -> constraint will be ignored..");
	}
	
	private String transformAttributeValue2Java(IBeXContextPattern context, IBeXConstant constant, Collection<HiPEAttribute> attributes) {
		return constant.getStringValue();
	}
	
	private String transformAttributeValue2Java(IBeXContextPattern context, IBeXAttributeExpression expr, Collection<HiPEAttribute> attributes) {
		if(expr.getAttribute().getEType().getInstanceClassName() == null) {
			throw new UnsupportedOperationException("IBeXEnumLiteral not supported in complex attribute constraints -> constraint will be ignored..");
		}
		
		if(expr.getAttribute().getEType().getInstanceClassName().equals("boolean")) {
			throw new UnsupportedOperationException("Boolean not supported in complex attribute constraints -> constraint will be ignored..");
		}
		
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		attr.setNode(transform(context, expr.getNode()));
		attr.setValue(expr.getAttribute());
		attr.setEAttribute(expr.getAttribute());
		attributes.add(attr);
		
		return expr.getNode().getName() + ".get" + expr.getAttribute().getName().substring(0, 1).toUpperCase() + expr.getAttribute().getName().substring(1) + "()";
	}
	
	private String transformAttributeValue2Java(IBeXContextPattern context, IBeXArithmeticExpression expr, Collection<HiPEAttribute> attributes) {
		if(expr instanceof IBeXArithmeticValueLiteral) {
			return transformAttributeValue2Java(context, (IBeXArithmeticValueLiteral)expr, attributes);
		} else if (expr instanceof IBeXArithmeticAttribute){
			return transformAttributeValue2Java(context, (IBeXArithmeticAttribute)expr, attributes);
		} else if (expr instanceof IBeXUnaryExpression) {
			IBeXUnaryExpression uexpr = (IBeXUnaryExpression)expr;
			StringBuilder sb = new StringBuilder();
			if(uexpr.isNegative()) {
				sb.append("-");
			}
			switch(uexpr.getOperator()) {
				case ABSOLUTE:
					sb.append("java.lang.Math.abs(");
					sb.append(transformAttributeValue2Java(context, uexpr.getOperand(), attributes));
					sb.append(")");
					break;
				case BRACKET:
					sb.append("(");
					sb.append(transformAttributeValue2Java(context, uexpr.getOperand(), attributes));
					sb.append(")");
					break;
				case COS:
					sb.append("java.lang.Math.cos(");
					sb.append(transformAttributeValue2Java(context, uexpr.getOperand(), attributes));
					sb.append(")");
					break;
				case COUNT:
					//TODO: Implement
					throw new UnsupportedOperationException("COUNT an operator type that is currently not supported by HiPE -> constraint will be ignored..");
				case EEXPONENTIAL:
					sb.append("java.lang.Math.exp(");
					sb.append(transformAttributeValue2Java(context, uexpr.getOperand(), attributes));
					sb.append(")");
					break;
				case LG:
					sb.append("java.lang.Math.log(");
					sb.append(transformAttributeValue2Java(context, uexpr.getOperand(), attributes));
					sb.append(")");
					break;
				case LOG:
					sb.append("java.lang.Math.log10(");
					sb.append(transformAttributeValue2Java(context, uexpr.getOperand(), attributes));
					sb.append(")");
					break;
				case SIN:
					sb.append("java.lang.Math.sin(");
					sb.append(transformAttributeValue2Java(context, uexpr.getOperand(), attributes));
					sb.append(")");
					break;
				case SQRT:
					sb.append("java.lang.Math.sqrt(");
					sb.append(transformAttributeValue2Java(context, uexpr.getOperand(), attributes));
					sb.append(")");
					break;
				case TAN:
					sb.append("java.lang.Math.tan(");
					sb.append(transformAttributeValue2Java(context, uexpr.getOperand(), attributes));
					sb.append(")");
					break;
				default:
					throw new UnsupportedOperationException("Unknown arithmetic operator type -> constraint will be ignored..");
			}
			return sb.toString();
		} else if (expr instanceof IBeXBinaryExpression) {
			IBeXBinaryExpression bexpr = (IBeXBinaryExpression)expr;
			StringBuilder sb = new StringBuilder();
			switch(bexpr.getOperator()) {
				case ADDITION:
					sb.append(transformAttributeValue2Java(context, bexpr.getLeft(), attributes));
					sb.append(" + ");
					sb.append(transformAttributeValue2Java(context, bexpr.getRight(), attributes));
					break;
				case DIVISION:
					sb.append(transformAttributeValue2Java(context, bexpr.getLeft(), attributes));
					sb.append(" / ");
					sb.append(transformAttributeValue2Java(context, bexpr.getRight(), attributes));
					break;
				case EXPONENTIATION:
					sb.append("java.lang.Math.pow(");
					sb.append(transformAttributeValue2Java(context, bexpr.getLeft(), attributes));
					sb.append(", ");
					sb.append(transformAttributeValue2Java(context, bexpr.getRight(), attributes));
					sb.append(")");
					break;
				case MAXIMUM:
					sb.append("java.lang.Math.max(");
					sb.append(transformAttributeValue2Java(context, bexpr.getLeft(), attributes));
					sb.append(", ");
					sb.append(transformAttributeValue2Java(context, bexpr.getRight(), attributes));
					sb.append(")");
					break;
				case MINIMUM:
					sb.append("java.lang.Math.min(");
					sb.append(transformAttributeValue2Java(context, bexpr.getLeft(), attributes));
					sb.append(", ");
					sb.append(transformAttributeValue2Java(context, bexpr.getRight(), attributes));
					sb.append(")");
					break;
				case MODULUS:
					sb.append(transformAttributeValue2Java(context, bexpr.getLeft(), attributes));
					sb.append(" % ");
					sb.append(transformAttributeValue2Java(context, bexpr.getRight(), attributes));
					break;
				case MULTIPLICATION:
					sb.append(transformAttributeValue2Java(context, bexpr.getLeft(), attributes));
					sb.append(" * ");
					sb.append(transformAttributeValue2Java(context, bexpr.getRight(), attributes));
					break;
				case SUBTRACTION:
					sb.append(transformAttributeValue2Java(context, bexpr.getLeft(), attributes));
					sb.append(" - ");
					sb.append(transformAttributeValue2Java(context, bexpr.getRight(), attributes));
					break;
				default:
					throw new UnsupportedOperationException("Unknown arithmetic operator type -> constraint will be ignored..");
			}
			return sb.toString();
		} else {
			throw new UnsupportedOperationException("Unknown arithmetic expression type -> constraint will be ignored..");
		}
		
	}
	
	private String transformAttributeValue2Java(IBeXContextPattern context, IBeXArithmeticValueLiteral expr, Collection<HiPEAttribute> attributes) {
		return String.valueOf(expr.getValue());
	}
	
	private String transformAttributeValue2Java(IBeXContextPattern context, IBeXArithmeticAttribute expr, Collection<HiPEAttribute> attributes) {
		if(expr.getAttribute().getEType().getInstanceClassName() == null) {
			throw new UnsupportedOperationException("IBeXEnumLiteral not supported in complex attribute constraints -> constraint will be ignored..");
		}
		
		if(expr.getAttribute().getEType().getInstanceClassName().equals("boolean")) {
			throw new UnsupportedOperationException("Boolean not supported in complex attribute constraints -> constraint will be ignored..");
		}
		
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		IBeXNode node = null;
		try {
			node =context.getSignatureNodes().stream()
					.filter(n -> n.getName().equals(expr.getName()))
					.findAny().get();
		} catch(Exception e) {
			throw new UnsupportedOperationException("Access to attibutes of non-signature nodes is prohibited -> constraint will be ignored..");
		}	
		
		attr.setNode(transform(context, node));
		attr.setValue(expr.getAttribute());
		attr.setEAttribute(expr.getAttribute());
		attributes.add(attr);
		
		return node.getName() + ".get" + expr.getAttribute().getName().substring(0, 1).toUpperCase() + expr.getAttribute().getName().substring(1) + "()";
	}
	
	private HiPEAttribute transform(IBeXContextPattern context, IBeXAttributeValue value) {
		if(value instanceof IBeXConstant)
			return transform(context, (IBeXConstant) value);
		if(value instanceof IBeXEnumLiteral)
			return transform(context, (IBeXEnumLiteral) value);
		if(value instanceof IBeXAttributeParameter)
			// TODO: implement attribute parameter
//			return transform((IBeXAttributeParameter) value);
			return null;
		if(value instanceof IBeXAttributeExpression)
			return transform(context, (IBeXAttributeExpression) value);
		return null;
	}
	
	private HiPEAttribute transform(IBeXContextPattern context, IBeXConstant constant) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		attr.setValue(constant.getValue());
		return attr;
	}
	
	private HiPEAttribute transform(IBeXContextPattern context, IBeXEnumLiteral literal) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		attr.setValue(literal.getLiteral());
		return attr;
	}
	
// TODO
//	private HiPEAttribute transform(IBeXContextPattern context, IBeXAttributeParameter attributeParam) {
//		HiPEAttribute attr = factory.createHiPEAttribute();
//		container.getAttributes().add(attr);
//		attr.setName(attributeParam.getName());
//		return attr;
//	}
	
	private HiPEAttribute transform(IBeXContextPattern context, IBeXAttributeExpression attributeExpr) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		attr.setNode(transform(context, attributeExpr.getNode()));
		attr.setValue(attributeExpr.getAttribute());
		attr.setEAttribute(attributeExpr.getAttribute());
		return attr;
	}

	private ComparatorType transform(IBeXContextPattern context, IBeXRelation relation) {
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
	
	private HiPEAttributeConstraint transform(IBeXContextPattern context, HiPEPattern pattern, IBeXCSP csp) {
		ComplexConstraint cConstraint = factory.createComplexConstraint();
		container.getAttributeConstraints().add(cConstraint);
		String initCode = csp.getPackage() + "." + getCSPName(csp.getName()) + " csp_" + csp_id + " = new " + csp.getPackage() + "." + getCSPName(csp.getName()) + "();\n";
		for(IBeXAttributeValue value : csp.getValues()) {
			initCode += "csp_" + csp_id + ".getVariables().add(new org.emoflon.ibex.tgg.operational.csp.RuntimeTGGAttributeConstraintVariable(true, ";
			if(value instanceof IBeXAttributeExpression ) {
				IBeXAttributeExpression iExpr = (IBeXAttributeExpression) value;
				String getOrIs = ".get";
				if(iExpr.getAttribute().getEType().getInstanceClassName() != null) {
					getOrIs = iExpr.getAttribute().getEType().getInstanceClassName().equals("boolean") ? ".is" : ".get";
				}
				initCode += iExpr.getNode().getName() + getOrIs + iExpr.getAttribute().getName().substring(0, 1).toUpperCase() + iExpr.getAttribute().getName().substring(1) + "()";
				HiPEAttribute hAttr = transform(context, iExpr);
				cConstraint.getAttributes().add(hAttr);
				pattern.getAttributes().add(hAttr);
				if(iExpr.getAttribute().getEType().getInstanceClassName() != null) {
					initCode += ", \"" + iExpr.getAttribute().getEType().getInstanceClassName() + "\"));\n";
				} else {
					initCode += ", \"Enum::" + iExpr.getAttribute().getEType().getName() + "\"));\n";
				}
			}
			if(value instanceof IBeXEnumLiteral) {
				IBeXEnumLiteral literal = (IBeXEnumLiteral) value;
				EEnum eenum = literal.getLiteral().getEEnum();
				
				IBeXConstant iConst= IBeXPatternModelFactory.eINSTANCE.createIBeXConstant();
				iConst.setValue(literal.getLiteral());
				iConst.setStringValue(eenum.getEPackage().getNsPrefix() + "." + eenum.getName() + "." + literal.getLiteral().getName());
				
				initCode += iConst.getStringValue().replaceAll("\"\"", "\"");
				HiPEAttribute hAttr = transform(context, iConst);
				cConstraint.getAttributes().add(hAttr);
				pattern.getAttributes().add(hAttr);
				initCode += ", \"" + iConst.getValue().getClass().getName() + "\"));\n";
			}
			if(value instanceof IBeXConstant) {
				IBeXConstant iConst= (IBeXConstant) value;
				initCode += iConst.getStringValue().replaceAll("\"\"", "\"");
				HiPEAttribute hAttr = transform(context, iConst);
				cConstraint.getAttributes().add(hAttr);
				pattern.getAttributes().add(hAttr);
				initCode += ", \"" + iConst.getValue().getClass().getName() + "\"));\n";
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
