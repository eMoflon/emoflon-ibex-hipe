package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import org.eclipse.emf.ecore.EEnum;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXAttributeValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXEnumValue;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXPattern;
import org.emoflon.ibex.common.coremodel.IBeXCoreModel.IBeXCoreArithmetic.ValueExpression;
import org.emoflon.ibex.gt.build.hipe.IBeXToHiPEPatternTransformation;
import org.emoflon.ibex.tgg.runtime.csp.RuntimeTGGAttributeConstraint;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.CSP.TGGAttributeConstraint;
import org.emoflon.ibex.tgg.tggmodel.IBeXTGGModel.CSP.TGGAttributeConstraintParameterValue;

import hipe.pattern.ComplexConstraint;
import hipe.pattern.HiPEAttribute;
import hipe.pattern.HiPEAttributeConstraint;
import hipe.pattern.HiPEPattern;

public class TGGToHiPEPatternTransformation extends IBeXToHiPEPatternTransformation {
	private int csp_id = 0;

	private HiPEAttributeConstraint transform(IBeXPattern ibexPattern, HiPEPattern hipePattern, TGGAttributeConstraint csp) {
		ComplexConstraint cConstraint = factory.createComplexConstraint();
		container.getAttributeConstraints().add(cConstraint);
		String initCode = csp.getPackage() + "." + getCSPName(csp.getDefinition().getName()) + " csp_" + csp_id + " = new " + csp.getPackage() + "." + getCSPName(csp.getName()) + "();\n";
		for(TGGAttributeConstraintParameterValue value : csp.getParameters()) {
			initCode += "csp_" + csp_id + ".getVariables().add(new org.emoflon.ibex.tgg.operational.csp.RuntimeTGGAttributeConstraintVariable(true, ";
			if(value instanceof IBeXAttributeValue attributeValue) {
				String getOrIs = ".get";
				if(attributeValue.getAttribute().getEType().getInstanceClassName() != null) {
					getOrIs = attributeValue.getAttribute().getEType().getInstanceClassName().equals("boolean") ? ".is" : ".get";
				}
				initCode += attributeValue.getNode().getName() + getOrIs + attributeValue.getAttribute().getName().substring(0, 1).toUpperCase() + iExpr.getAttribute().getName().substring(1) + "()";
				HiPEAttribute hAttr = transformSimple(ibexPattern, attributeValue);
				cConstraint.getAttributes().add(hAttr);
				hipePattern.getAttributes().add(hAttr);
				if(attributeValue.getAttribute().getEType().getInstanceClassName() != null) {
					initCode += ", \"" + attributeValue.getAttribute().getEType().getInstanceClassName() + "\"));\n";
				} else {
					initCode += ", \"Enum::" + attributeValue.getAttribute().getEType().getName() + "\"));\n";
				}
			}
			if(value instanceof IBeXEnumValue literal) {
				EEnum eenum = literal.getLiteral().getEEnum();
				
				IBeXConstant iConst = IBeXPatternModelFactory.eINSTANCE.createIBeXConstant();
				iConst.setValue(literal.getLiteral());
				iConst.setStringValue(eenum.getEPackage().getNsPrefix() + "." + eenum.getName() + "." + literal.getLiteral().getName());
				
				initCode += iConst.getStringValue().replaceAll("\"\"", "\"");
				HiPEAttribute hAttr = transform(ibexPattern, iConst);
				cConstraint.getAttributes().add(hAttr);
				hipePattern.getAttributes().add(hAttr);
				initCode += ", \"" + iConst.getValue().getClass().getName() + "\"));\n";
			}
			
			if(value instanceof ValueExpression valueExpression) {
				IBeXConstant iConst= (IBeXConstant) value;
				initCode += iConst.getStringValue().replaceAll("\"", "\"");
				HiPEAttribute hAttr = transform(ibexPattern, iConst);
				cConstraint.getAttributes().add(hAttr);
				hipePattern.getAttributes().add(hAttr);
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
