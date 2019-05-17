package org.emoflon.ibex.tgg.ide.hipe.visualisation;

import hipe.pattern.HiPENode;
import hipe.pattern.HiPEPattern;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.xtend2.lib.StringConcatenation;
import org.emoflon.ibex.tgg.ide.visualisation.IbexPlantUMLGenerator;

@SuppressWarnings("all")
public class IbexHiPEPlantUMLGenerator extends IbexPlantUMLGenerator {
  public static String separator() {
    return "_";
  }
  
  public static String visualisePatternBody(final HiPEPattern b, final String prefix) {
    throw new Error("Unresolved compilation problems:"
      + "\nThe method or field signatureNodes is undefined for the type HiPEPattern");
  }
  
  private static String visualiseSymbolicParameters(final HiPEPattern p, final String prefix) {
    throw new Error("Unresolved compilation problems:"
      + "\nThe method or field signatureNodes is undefined for the type HiPEPattern");
  }
  
  private static String visualiseIsolatedPatternBody(final HiPEPattern b, final String prefix) {
    throw new Error("Unresolved compilation problems:"
      + "\nThe method or field localNodes is undefined for the type HiPEPattern");
  }
  
  private static CharSequence identifierFor(final HiPENode v, final HiPEPattern pattern, final String prefix) {
    StringConcatenation _builder = new StringConcatenation();
    _builder.append("\"");
    _builder.append(prefix);
    String _name = pattern.getName();
    _builder.append(_name);
    _builder.append(".");
    String _name_1 = v.getName();
    _builder.append(_name_1);
    _builder.append(":");
    String _extractType = IbexHiPEPlantUMLGenerator.extractType(v.eClass());
    _builder.append(_extractType);
    _builder.append("\"");
    return _builder;
  }
  
  public static String extractType(final EClassifier classifier) {
    String _xifexpression = null;
    if (((classifier == null) || (classifier.getName() == null))) {
      _xifexpression = "???";
    } else {
      _xifexpression = classifier.getName();
    }
    return _xifexpression;
  }
}
