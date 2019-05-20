package org.emoflon.ibex.tgg.ide.hipe.visualisation

import org.emoflon.ibex.tgg.ide.visualisation.IbexPlantUMLGenerator
import org.eclipse.emf.ecore.EClassifier
import hipe.pattern.HiPEPattern
import hipe.pattern.HiPENode

class IbexHiPEPlantUMLGenerator extends IbexPlantUMLGenerator {

	static def String separator() {
		return "_"
	}

	static def String visualisePatternBody(HiPEPattern b, String prefix) {
		'''
			«visualiseIsolatedPatternBody(b, prefix)»
			«var j = 0»
			«FOR pi : b.patternInvocations»
				«var subPrefix = prefix + separator() + j++ + separator()»
				«visualisePatternBody(pi.invokedPattern, subPrefix)»
				«FOR param : pi.invokedPattern.nodes.filter[n | !n.local]»
					«IF pi.positive»
						«identifierFor(param, b, prefix)» #--#
					«ELSE»
						namespace «subPrefix»«pi.invokedPattern.name» #DDDDDD {
						«identifierFor(param, b, prefix)» #..#
						}
					«ENDIF»
				«ENDFOR»
			«ENDFOR»
		'''
	}

	private static def String visualiseSymbolicParameters(HiPEPattern p, String prefix) {
		'''
			«FOR v : p.nodes.filter[n | !n.local] SEPARATOR "\n"»
				class «identifierFor(v, p, prefix)»<< (V,#FF7700)>>
			«ENDFOR»
		'''
	}

	private static def String visualiseIsolatedPatternBody(HiPEPattern b, String prefix) {
		'''
			«visualiseSymbolicParameters(b, prefix)»
			«FOR v : b.nodes.filter[n | n.local] SEPARATOR "\n"»
				class «identifierFor(v, b, prefix)»<< (L,#B0D8F0)>>
			«ENDFOR»
			«FOR ref : b.edges»
				«identifierFor(ref.source, b, prefix)» --> «identifierFor(ref.target, b, prefix)»
			«ENDFOR»
		'''
	}

	private static def identifierFor(HiPENode v, HiPEPattern pattern, String prefix) {
		'''"«prefix»«pattern.name».«v.name»:«extractType(v.eClass)»"'''
	}

	def static extractType(EClassifier classifier) {
		if(classifier === null || classifier.name === null) "???" else classifier.name
	}

}
