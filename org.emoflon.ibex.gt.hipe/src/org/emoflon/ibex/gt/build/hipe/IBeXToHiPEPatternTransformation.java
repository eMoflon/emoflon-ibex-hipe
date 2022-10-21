package org.emoflon.ibex.gt.build.hipe;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EcorePackage;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXAttributeValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXBooleanValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXEdge;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXEnumValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXMatchCountValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXNode;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXNodeValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXNullValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXPattern;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXPatternInvocation;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXStringValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.ArithmeticExpression;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.BinaryExpression;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.BooleanBinaryExpression;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.BooleanExpression;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.BooleanUnaryExpression;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.DoubleLiteral;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.IntegerLiteral;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.RelationalExpression;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.RelationalOperator;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.UnaryExpression;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.ValueExpression;
import org.emoflon.ibex.common.transformation.DataTypeUtil;
import org.emoflon.ibex.gt.gtmodel.IBeXGTModel.GTModel;
import org.emoflon.ibex.gt.gtmodel.IBeXGTModel.GTPattern;

import hipe.pattern.ComparatorType;
import hipe.pattern.ComplexConstraint;
import hipe.pattern.EqualConstraint;
import hipe.pattern.HiPEAttribute;
import hipe.pattern.HiPEAttributeConstraint;
import hipe.pattern.HiPEContainer;
import hipe.pattern.HiPECountInvocation;
import hipe.pattern.HiPEEdge;
import hipe.pattern.HiPENode;
import hipe.pattern.HiPEPattern;
import hipe.pattern.HiPEPatternFactory;
import hipe.pattern.HiPEPatternInvocation;
import hipe.pattern.RelationalConstraint;
import hipe.pattern.UnequalConstraint;

public class IBeXToHiPEPatternTransformation {

//	private static Logger logger = Logger.getLogger(GTHiPEBuilderExtension.class);

	private HiPEPatternFactory factory;
	protected GTModel model;
	protected Map<String, HiPEPattern> name2pattern = new HashMap<>();
	protected Map<IBeXNode, HiPENode> node2node = new HashMap<>();
	protected HiPEContainer container;
	protected Map<IBeXPattern, HiPEPattern> ibexPattern2pattern = new HashMap<>();
	protected Map<HiPECountInvocation, IBeXMatchCountValue> hipeCount2Count = new HashMap<>();

//	private int csp_id = 0;

	public HiPEContainer transform(GTModel model) {
		factory = HiPEPatternFactory.eINSTANCE;
		this.model = model;
		name2pattern = new HashMap<>();
		node2node = new HashMap<>();

		container = factory.createHiPEContainer();
		for (IBeXPattern pattern : model.getPatternSet().getPatterns()) {
			if (pattern.getSignatureNodes().isEmpty() && pattern.getLocalNodes().isEmpty())
				continue;

			container.getPatterns().add(transform((GTPattern) pattern));
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

	public HiPEPattern transform(GTPattern context) {
		context.setName(context.getName().replace("-", "_"));

		if (name2pattern.containsKey(context.getName()))
			return name2pattern.get(context.getName());

		HiPEPattern pattern = factory.createHiPEPattern();
		pattern.setName(context.getName());

		name2pattern.put(pattern.getName(), pattern);

		for (IBeXPatternInvocation inv : context.getInvocations()) {
			HiPEPatternInvocation invocation = factory.createHiPEPatternInvocation();

			HiPEPattern invoked = transform((GTPattern) inv.getInvocation());
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

		// Get the remaining complex constraints and generate code if the pattern does
		// not contain any unsupported complex attribute expressions (e.g., parameter
		// expressions)
		if (!context.getUsedFeatures().isParameterExpressions()) {
			for (BooleanExpression constr : context.getConditions().stream().filter(cond -> !transformed.contains(cond))
					.collect(Collectors.toList())) {
				HiPEAttributeConstraint constraint = transformComplexAC(context, pattern, constr);

				if (constraint != null)
					pattern.getAttributeConstraints().add(constraint);
			}
		}
		// TODO: This is a tgg speciality and should be performed in the corresponding
		// tgg2hipe transformation!
//		for(IBeXCSP csp : context.getCsps()) {
//			pattern.getAttributeConstraints().add(transform(context, pattern, csp));
//		}

		ibexPattern2pattern.put(context, pattern);
		return pattern;
	}

	public boolean isSimpleNodeConstraint(BooleanExpression expr) {
		if (expr instanceof RelationalExpression relExpr && (relExpr.getOperator() == RelationalOperator.OBJECT_EQUALS
				|| relExpr.getOperator() == RelationalOperator.OBJECT_NOT_EQUALS)) {
			if (relExpr.getLhs() instanceof IBeXNodeValue && relExpr.getRhs() instanceof IBeXNodeValue) {
				return true;
			}
			return false;
		}
		return false;
	}

	public boolean isSimpleAttributeConstraint(BooleanExpression expr) {
		if (expr instanceof RelationalExpression relExpr && !(relExpr.getOperator() == RelationalOperator.OBJECT_EQUALS
				|| relExpr.getOperator() == RelationalOperator.OBJECT_NOT_EQUALS)) {
			if (isSimpleAttributeValue(relExpr.getLhs()) && isSimpleAttributeValue(relExpr.getRhs())) {
				return true;
			}
			return false;
		}
		return false;
	}

	public boolean isSimpleAttributeValue(ValueExpression value) {
		if (value instanceof IBeXStringValue)
			return true;
		if (value instanceof IBeXEnumValue)
			return true;
		if (value instanceof IBeXBooleanValue)
			return true;
		if (value instanceof IBeXNullValue)
			return true;
		if (value instanceof DoubleLiteral)
			return true;
		if (value instanceof IntegerLiteral)
			return true;
		if (value instanceof IBeXAttributeValue)
			return true;

		return false;
	}

	public HiPENode transform(IBeXPattern context, IBeXNode node) {
		if (node2node.containsKey(node))
			return node2node.get(node);
		HiPENode hNode = factory.createHiPENode();
		container.getNodes().add(hNode);
		hNode.setName(node.getName());
		hNode.setType(node.getType());
		hNode.setLocal(context.getLocalNodes().contains(node));

		node2node.put(node, hNode);

		return hNode;
	}

	private HiPEEdge transform(IBeXPattern context, IBeXEdge edge) {
		HiPEEdge hEdge = factory.createHiPEEdge();
		container.getEdges().add(hEdge);
		hEdge.setName(edge.getSource().getType().getName() + "_" + edge.getType().getName());
		hEdge.setType(edge.getType());
		hEdge.setSource(transform(context, edge.getSource()));
		hEdge.setTarget(transform(context, edge.getTarget()));
		return hEdge;
	}

	private UnequalConstraint transformUE(IBeXPattern context, RelationalExpression pair) {
		UnequalConstraint constr = factory.createUnequalConstraint();
		container.getNodeConstraints().add(constr);
		constr.setLeftNode(transform(context, ((IBeXNodeValue) pair.getLhs()).getNode()));
		constr.setRightNode(transform(context, ((IBeXNodeValue) pair.getRhs()).getNode()));
		return constr;
	}

	private EqualConstraint transformE(IBeXPattern context, RelationalExpression pair) {
		EqualConstraint constr = factory.createEqualConstraint();
		container.getNodeConstraints().add(constr);
		constr.setLeftNode(transform(context, ((IBeXNodeValue) pair.getLhs()).getNode()));
		constr.setRightNode(transform(context, ((IBeXNodeValue) pair.getRhs()).getNode()));
		return constr;
	}

	private HiPEAttributeConstraint transformSimpleAC(IBeXPattern context, HiPEPattern pattern,
			RelationalExpression constr) {
		RelationalConstraint rConstraint = factory.createRelationalConstraint();
		HiPEAttribute attrLeft = transformSimple(context, constr.getLhs());
		HiPEAttribute attrRight = transformSimple(context, constr.getRhs());
		if (attrLeft != null) {
			pattern.getAttributes().add(attrLeft);
			rConstraint.setLeftAttribute(attrLeft);
		}
		if (attrRight != null) {
			pattern.getAttributes().add(attrRight);
			rConstraint.setRightAttribute(attrRight);
		}
		rConstraint.setType(transform(context, constr.getOperator()));

		if (attrLeft == null || attrRight == null)
			return null;

		container.getAttributeConstraints().add(rConstraint);

		pattern.getAttributes().add(rConstraint.getLeftAttribute());
		pattern.getAttributes().add(rConstraint.getRightAttribute());

		return rConstraint;
	}

	private HiPEAttributeConstraint transformComplexAC(IBeXPattern context, HiPEPattern pattern,
			BooleanExpression constr) {
		ComplexConstraint cConstraint = factory.createComplexConstraint();
		Collection<HiPEAttribute> attributes = new HashSet<>();

		String expression = transform2Java(constr, context, cConstraint, attributes);
		cConstraint.getAttributes().addAll(attributes);
		cConstraint.setInitializationCode("");
		cConstraint.setPredicateCode(expression);

		container.getAttributeConstraints().add(cConstraint);
		return cConstraint;
	}

	private String transform2Java(BooleanExpression expression, IBeXPattern context, ComplexConstraint cConstraint,
			Collection<HiPEAttribute> attributes) {
		if (expression instanceof BooleanBinaryExpression bool) {
			switch (bool.getOperator()) {
			case AND: {
				return transform2Java(bool.getLhs(), context, cConstraint, attributes) + "&&"
						+ transform2Java(bool.getRhs(), context, cConstraint, attributes);
			}
			case IMPLICATION: {
				return "(!(" + transform2Java(bool.getLhs(), context, cConstraint, attributes) + ") || "
						+ transform2Java(bool.getRhs(), context, cConstraint, attributes) + ")";
			}
			case OR: {
				return transform2Java(bool.getLhs(), context, cConstraint, attributes) + "||"
						+ transform2Java(bool.getRhs(), context, cConstraint, attributes);
			}
			case XOR: {
				return transform2Java(bool.getLhs(), context, cConstraint, attributes) + "^"
						+ transform2Java(bool.getRhs(), context, cConstraint, attributes);
			}
			default:
				throw new UnsupportedOperationException("Unknown boolean expression type: " + expression);
			}
		} else if (expression instanceof BooleanUnaryExpression unary) {
			switch (unary.getOperator()) {
			case BRACKET: {
				return "(" + transform2Java(unary.getOperand(), context, cConstraint, attributes) + ")";
			}
			case NEGATION: {
				return "!(" + transform2Java(unary.getOperand(), context, cConstraint, attributes) + ")";
			}
			default:
				throw new UnsupportedOperationException("Unknown boolean expression type: " + expression);
			}
		} else if (expression instanceof IBeXBooleanValue boolVal) {
			return boolVal.toString();
		} else if (expression instanceof IBeXNullValue) {
			return "null";
		} else if (expression instanceof IBeXAttributeValue atrVal) {
			return transformAttributeValue2Java(context, atrVal, attributes);
		} else if (expression instanceof RelationalExpression relExpr) {
			switch (relExpr.getOperator()) {
			case EQUAL: {
				return transform2Java(relExpr.getLhs(), context, cConstraint, attributes) + " == "
						+ transform2Java(relExpr.getRhs(), context, cConstraint, attributes);
			}
			case GREATER: {
				return transform2Java(relExpr.getLhs(), context, cConstraint, attributes) + " > "
						+ transform2Java(relExpr.getRhs(), context, cConstraint, attributes);
			}
			case GREATER_OR_EQUAL: {
				return transform2Java(relExpr.getLhs(), context, cConstraint, attributes) + " >= "
						+ transform2Java(relExpr.getRhs(), context, cConstraint, attributes);
			}
			case OBJECT_EQUALS: {
				return "(" + transform2Java(relExpr.getLhs(), context, cConstraint, attributes) + ").equals("
						+ transform2Java(relExpr.getRhs(), context, cConstraint, attributes) + ")";
			}
			case OBJECT_NOT_EQUALS: {
				return "!(" + transform2Java(relExpr.getLhs(), context, cConstraint, attributes) + ").equals("
						+ transform2Java(relExpr.getRhs(), context, cConstraint, attributes) + ")";
			}
			case SMALLER: {
				return transform2Java(relExpr.getLhs(), context, cConstraint, attributes) + " < "
						+ transform2Java(relExpr.getRhs(), context, cConstraint, attributes);
			}
			case SMALLER_OR_EQUAL: {
				return transform2Java(relExpr.getLhs(), context, cConstraint, attributes) + " <= "
						+ transform2Java(relExpr.getRhs(), context, cConstraint, attributes);
			}
			case UNEQUAL: {
				return transform2Java(relExpr.getLhs(), context, cConstraint, attributes) + " != "
						+ transform2Java(relExpr.getRhs(), context, cConstraint, attributes);
			}
			default:
				throw new UnsupportedOperationException("Unknown boolean expression type: " + expression);
			}
		} else {
			throw new UnsupportedOperationException("Unknown boolean expression type: " + expression);
		}
	}

	private String transform2Java(ValueExpression expression, IBeXPattern context, ComplexConstraint cConstraint,
			Collection<HiPEAttribute> attributes) {
		if (expression instanceof IBeXBooleanValue bool) {
			return Boolean.toString(bool.isValue());
		} else if (expression instanceof IBeXEnumValue enm) {
			return model.getMetaData().getName2package().get(enm.getType().getEPackage().getName())
					.getClassifierName2FQN().get(enm.getType().getName()) + "." + enm.getType().getName() + "."
					+ enm.getLiteral().getName();
		} else if (expression instanceof IBeXStringValue str) {
			return str.getValue();
		} else if (expression instanceof IBeXNullValue) {
			return "null";
		} else if (expression instanceof ArithmeticExpression aExpr) {
			return transform2Java(aExpr, context, cConstraint, attributes);
		} else {
			throw new UnsupportedOperationException("Unknown value expression type: " + expression);
		}
	}

	private String transform2Java(ArithmeticExpression expression, IBeXPattern context, ComplexConstraint cConstraint,
			Collection<HiPEAttribute> attributes) {
		if (expression instanceof IBeXAttributeValue atrVal) {
			return transformAttributeValue2Java(context, atrVal, attributes);
		} else if (expression instanceof IBeXNodeValue nVal) {
			return transformNodeValue2Java(context, nVal, attributes);
		} else if (expression instanceof IBeXMatchCountValue cVal) {
			return transformCountValue2Java(context, cConstraint, cVal, attributes);
		} else if (expression instanceof BinaryExpression bin) {
			switch (bin.getOperator()) {
			case ADD: {
				return getCast(expression, bin.getLhs())
						+ transform2Java(bin.getLhs(), context, cConstraint, attributes) + " + "
						+ getCast(expression, bin.getRhs())
						+ transform2Java(bin.getRhs(), context, cConstraint, attributes);
			}
			case DIVIDE: {
				return getCast(expression, bin.getLhs())
						+ transform2Java(bin.getLhs(), context, cConstraint, attributes) + " / "
						+ getCast(expression, bin.getRhs())
						+ transform2Java(bin.getRhs(), context, cConstraint, attributes);
			}
			case LOG: {
				return "(java.lang.Math.log(" + getCast(expression, bin.getLhs())
						+ transform2Java(bin.getLhs(), context, cConstraint, attributes) + ") / java.lang.Math.log("
						+ getCast(expression, bin.getRhs())
						+ transform2Java(bin.getRhs(), context, cConstraint, attributes) + "))";
			}
			case MAX: {
				return "java.lang.Math.max(" + getCast(expression, bin.getLhs())
						+ transform2Java(bin.getLhs(), context, cConstraint, attributes) + ", "
						+ getCast(expression, bin.getRhs())
						+ transform2Java(bin.getRhs(), context, cConstraint, attributes) + ")";
			}
			case MIN: {
				return "java.lang.Math.min(" + getCast(expression, bin.getLhs())
						+ transform2Java(bin.getLhs(), context, cConstraint, attributes) + ", "
						+ getCast(expression, bin.getRhs())
						+ transform2Java(bin.getRhs(), context, cConstraint, attributes) + ")";
			}
			case MOD: {
				return getCast(expression, bin.getLhs())
						+ transform2Java(bin.getLhs(), context, cConstraint, attributes) + " % "
						+ getCast(expression, bin.getRhs())
						+ transform2Java(bin.getRhs(), context, cConstraint, attributes);
			}
			case MULTIPLY: {
				return getCast(expression, bin.getLhs())
						+ transform2Java(bin.getLhs(), context, cConstraint, attributes) + " * "
						+ getCast(expression, bin.getRhs())
						+ transform2Java(bin.getRhs(), context, cConstraint, attributes);
			}
//				case NORMAL_DISTRIBUTION: {
//					return '''gtEngine.rndGenerator.nextGaussian(«getCast(expression, expression.lhs)»«unparse(methodContext, expression.lhs)», «getCast(expression, expression.rhs)»«unparse(methodContext, expression.rhs)»)'''
//				}
			case POW: {
				return "java.lang.Math.pow(" + getCast(expression, bin.getLhs())
						+ transform2Java(bin.getLhs(), context, cConstraint, attributes) + ", "
						+ getCast(expression, bin.getRhs())
						+ transform2Java(bin.getRhs(), context, cConstraint, attributes) + ")";
			}
			case SUBTRACT: {
				return getCast(expression, bin.getLhs())
						+ transform2Java(bin.getLhs(), context, cConstraint, attributes) + " - "
						+ getCast(expression, bin.getRhs())
						+ transform2Java(bin.getRhs(), context, cConstraint, attributes);
			}
//				case UNIFORM_DISTRIBUTION: {
//					return '''(gtEngine.rndGenerator.nextDouble() * («getCast(expression, expression.rhs)»«unparse(methodContext, expression.rhs)» - «getCast(expression, expression.lhs)»«unparse(methodContext, expression.lhs)») + «getCast(expression, expression.lhs)»«unparse(methodContext, expression.lhs)»)'''
//				}
			default:
				throw new UnsupportedOperationException("Unknown arithmetic expression type: " + expression);
			}
		} else if (expression instanceof UnaryExpression un) {
			switch (un.getOperator()) {
			case ABSOLUTE: {
				return "java.lang.Math.abs(" + getCast(expression, un.getOperand())
						+ transform2Java(un.getOperand(), context, cConstraint, attributes) + ")";
			}
			case BRACKET: {
				return "(" + getCast(expression, un.getOperand())
						+ transform2Java(un.getOperand(), context, cConstraint, attributes) + ")";
			}
			case COS: {
				return "java.lang.Math.cos(" + getCast(expression, un.getOperand())
						+ transform2Java(un.getOperand(), context, cConstraint, attributes) + ")";
			}
//				case EXPONENTIAL_DISTRIBUTION: {
//					return '''(Math.log(1 - gtEngine.rndGenerator.nextDouble()) / (-«getCast(expression, expression.operand)»«unparse(methodContext, expression.operand)»))'''
//				}
			case NEGATIVE: {
				return "-(" + getCast(expression, un.getOperand())
						+ transform2Java(un.getOperand(), context, cConstraint, attributes) + ")";
			}
			case SIN: {
				return "java.lang.Math.sin(" + getCast(expression, un.getOperand())
						+ transform2Java(un.getOperand(), context, cConstraint, attributes) + ")";
			}
			case SQRT: {
				return "java.lang.Math.sqrt(" + getCast(expression, un.getOperand())
						+ transform2Java(un.getOperand(), context, cConstraint, attributes) + ")";
			}
			case TAN: {
				return "java.lang.Math.tan(" + getCast(expression, un.getOperand())
						+ transform2Java(un.getOperand(), context, cConstraint, attributes) + ")";
			}
			default:
				throw new UnsupportedOperationException("Unknown arithmetic expression type: " + expression);
			}
		} else if (expression instanceof DoubleLiteral dbl) {
			return Double.toString(dbl.getValue());
		} else if (expression instanceof IntegerLiteral intgr) {
			return Integer.toString(intgr.getValue());
		} else {
			throw new UnsupportedOperationException("Unknown arithmetic expression type: " + expression);
		}
	}

	private String transformCountValue2Java(IBeXPattern context, ComplexConstraint cConstraint,
			IBeXMatchCountValue countVal, Collection<HiPEAttribute> attributes) {
		HiPECountInvocation hipeCount = factory.createHiPECountInvocation();
		cConstraint.getCountInvocations().add(hipeCount);
		hipeCount2Count.put(hipeCount, countVal);

		StringBuilder nestedSB = new StringBuilder();
		nestedSB.append("COUNT_");
		nestedSB.append(countVal.getInvocation().getInvocation().getName());
		for (Entry<IBeXNode, IBeXNode> node : countVal.getInvocation().getMapping()) {
			nestedSB.append("_");
			nestedSB.append(node.getKey().getName());
			nestedSB.append("2");
			nestedSB.append(node.getValue().getName());
		}

		hipeCount.setExpressionID(nestedSB.toString());
		StringBuilder sb = new StringBuilder();
		sb.append("getCount(match, ");
		sb.append(hipeCount.getExpressionID());
		sb.append(")");
		return sb.toString();
	}

	private String transformAttributeValue2Java(IBeXPattern context, IBeXAttributeValue expr,
			Collection<HiPEAttribute> attributes) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		IBeXNode node = expr.getNode();

		attr.setNode(transform(context, node));
		attr.setValue(expr.getAttribute());
		attr.setEAttribute(expr.getAttribute());
		attributes.add(attr);

		return node.getName() + ".get" + expr.getAttribute().getName().substring(0, 1).toUpperCase()
				+ expr.getAttribute().getName().substring(1) + "()";
	}

	private String transformNodeValue2Java(IBeXPattern context, IBeXNodeValue expr,
			Collection<HiPEAttribute> attributes) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		IBeXNode node = expr.getNode();

		attr.setNode(transform(context, node));
		attributes.add(attr);

		return node.getName();
	}

	private HiPEAttribute transformSimple(IBeXPattern context, ValueExpression value) {
		if (value instanceof IBeXStringValue str)
			return transformSimple(context, str);
		if (value instanceof IBeXEnumValue enm)
			return transformSimple(context, enm);
		if (value instanceof IBeXBooleanValue bool)
			return transformSimple(context, bool);
		if (value instanceof IBeXNullValue nll)
			return transformSimple(context, nll);
		if (value instanceof DoubleLiteral dbl)
			return transformSimple(context, dbl);
		if (value instanceof IntegerLiteral intgr)
			return transformSimple(context, intgr);
		if (value instanceof IBeXAttributeValue atrVal)
			return transformSimple(context, atrVal);

		return null;
	}

	private HiPEAttribute transformSimple(IBeXPattern context, IBeXStringValue constant) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		attr.setValue(constant.getValue());
		return attr;
	}

	private HiPEAttribute transformSimple(IBeXPattern context, IBeXEnumValue constant) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		attr.setValue(constant.getLiteral());
		return attr;
	}

	private HiPEAttribute transformSimple(IBeXPattern context, IBeXBooleanValue constant) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		attr.setValue(constant.isValue());
		return attr;
	}

	private HiPEAttribute transformSimple(IBeXPattern context, IBeXNullValue constant) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		attr.setValue(null);
		return attr;
	}

	private HiPEAttribute transformSimple(IBeXPattern context, DoubleLiteral constant) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		attr.setValue(constant.getValue());
		return attr;
	}

	private HiPEAttribute transformSimple(IBeXPattern context, IntegerLiteral constant) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		attr.setValue(constant.getValue());
		return attr;
	}

	private HiPEAttribute transformSimple(IBeXPattern context, IBeXAttributeValue attributeExpr) {
		HiPEAttribute attr = factory.createHiPEAttribute();
		container.getAttributes().add(attr);
		attr.setNode(transform(context, attributeExpr.getNode()));
		attr.setValue(attributeExpr.getAttribute());
		attr.setEAttribute(attributeExpr.getAttribute());
		return attr;
	}

	private ComparatorType transform(IBeXPattern context, RelationalOperator relation) {
		switch (relation) {
		case EQUAL:
			return ComparatorType.EQUAL;
		case GREATER:
			return ComparatorType.GREATER;
		case GREATER_OR_EQUAL:
			return ComparatorType.GREATER_OR_EQUAL;
		case SMALLER:
			return ComparatorType.LESS;
		case SMALLER_OR_EQUAL:
			return ComparatorType.LESS_OR_EQUAL;
		case UNEQUAL:
			return ComparatorType.UNEQUAL;
		default:
			return null;
		}
	}

	private String getCast(ArithmeticExpression parent, ArithmeticExpression leaf) {
		if (parent.getType().equals(leaf.getType())) {
			return "";
		} else {
			return EDataType2Java(parent.getType());
		}
	}

	private String EDataType2Java(EClassifier type) {
		EClassifier simplifiedType = DataTypeUtil.simplifiyType(type);
		if (simplifiedType == EcorePackage.Literals.ELONG) {
			return "(long)";
		} else if (simplifiedType == EcorePackage.Literals.EDOUBLE) {
			return "(double)";
		} else if (simplifiedType == EcorePackage.Literals.ESTRING) {
			return "(String)";
		} else if (simplifiedType == EcorePackage.Literals.EBOOLEAN) {
			return "(boolean)";
		} else if (simplifiedType == EcorePackage.Literals.EDATE) {
			return "(java.util.Date)";
		} else if (type instanceof EClass) {
			return "(" + model.getMetaData().getName2package().get(type.getEPackage().getName()).getClassifierName2FQN()
					.get(type.getName()) + ")";
		} else {
			throw new IllegalArgumentException("Unknown or unsupported data type: " + type);
		}
	}

	// TODO: This is a tgg speciality and should be performed in the corresponding
//	private HiPEAttributeConstraint transform(IBeXContextPattern context, HiPEPattern pattern, IBeXCSP csp) {
//		ComplexConstraint cConstraint = factory.createComplexConstraint();
//		container.getAttributeConstraints().add(cConstraint);
//		String initCode = csp.getPackage() + "." + getCSPName(csp.getName()) + " csp_" + csp_id + " = new "
//				+ csp.getPackage() + "." + getCSPName(csp.getName()) + "();\n";
//		for (IBeXAttributeValue value : csp.getValues()) {
//			initCode += "csp_" + csp_id
//					+ ".getVariables().add(new org.emoflon.ibex.tgg.operational.csp.RuntimeTGGAttributeConstraintVariable(true, ";
//			if (value instanceof IBeXAttributeExpression) {
//				IBeXAttributeExpression iExpr = (IBeXAttributeExpression) value;
//				String getOrIs = ".get";
//				if (iExpr.getAttribute().getEType().getInstanceClassName() != null) {
//					getOrIs = iExpr.getAttribute().getEType().getInstanceClassName().equals("boolean") ? ".is" : ".get";
//				}
//				initCode += iExpr.getNode().getName() + getOrIs
//						+ iExpr.getAttribute().getName().substring(0, 1).toUpperCase()
//						+ iExpr.getAttribute().getName().substring(1) + "()";
//				HiPEAttribute hAttr = transform(context, iExpr);
//				cConstraint.getAttributes().add(hAttr);
//				pattern.getAttributes().add(hAttr);
//				if (iExpr.getAttribute().getEType().getInstanceClassName() != null) {
//					initCode += ", \"" + iExpr.getAttribute().getEType().getInstanceClassName() + "\"));\n";
//				} else {
//					initCode += ", \"Enum::" + iExpr.getAttribute().getEType().getName() + "\"));\n";
//				}
//			}
//			if (value instanceof IBeXEnumLiteral) {
//				IBeXEnumLiteral literal = (IBeXEnumLiteral) value;
//				EEnum eenum = literal.getLiteral().getEEnum();
//
//				IBeXConstant iConst = IBeXPatternModelFactory.eINSTANCE.createIBeXConstant();
//				iConst.setValue(literal.getLiteral());
//				iConst.setStringValue(eenum.getEPackage().getNsPrefix() + "." + eenum.getName() + "."
//						+ literal.getLiteral().getName());
//
//				initCode += iConst.getStringValue().replaceAll("\"\"", "\"");
//				HiPEAttribute hAttr = transform(context, iConst);
//				cConstraint.getAttributes().add(hAttr);
//				pattern.getAttributes().add(hAttr);
//				initCode += ", \"" + iConst.getValue().getClass().getName() + "\"));\n";
//			}
//			if (value instanceof IBeXConstant) {
//				IBeXConstant iConst = (IBeXConstant) value;
//				initCode += iConst.getStringValue().replaceAll("\"", "\"");
//				HiPEAttribute hAttr = transform(context, iConst);
//				cConstraint.getAttributes().add(hAttr);
//				pattern.getAttributes().add(hAttr);
//				initCode += ", \"" + iConst.getValue().getClass().getName() + "\"));\n";
//			}
//		}
//		initCode += "csp_" + csp_id + ".solve();\n";
//
//		cConstraint.setInitializationCode(initCode);
//		cConstraint.setPredicateCode("csp_" + csp_id + ".isSatisfied()");
//
//		csp_id++;
//		return cConstraint;
//	}
//
//	private String getCSPName(String name) {
//		if (name.startsWith("eq_"))
//			return "Eq";
//
//		return name.substring(0, 1).toUpperCase() + name.substring(1);
//	}

}
