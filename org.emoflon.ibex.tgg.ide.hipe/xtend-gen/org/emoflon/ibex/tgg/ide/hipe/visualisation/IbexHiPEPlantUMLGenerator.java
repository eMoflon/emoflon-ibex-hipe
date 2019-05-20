package org.emoflon.ibex.tgg.ide.hipe.visualisation;

import hipe.pattern.HiPEEdge;
import hipe.pattern.HiPENode;
import hipe.pattern.HiPEPattern;
import hipe.pattern.HiPEPatternInvocation;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.xtend2.lib.StringConcatenation;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.emoflon.ibex.tgg.ide.visualisation.IbexPlantUMLGenerator;

@SuppressWarnings("all")
public class IbexHiPEPlantUMLGenerator extends IbexPlantUMLGenerator {
  public static String separator() {
    return "_";
  }
  
  public static String visualisePatternBody(final HiPEPattern b, final String prefix) {
    StringConcatenation _builder = new StringConcatenation();
    String _visualiseIsolatedPatternBody = IbexHiPEPlantUMLGenerator.visualiseIsolatedPatternBody(b, prefix);
    _builder.append(_visualiseIsolatedPatternBody);
    _builder.newLineIfNotEmpty();
    int j = 0;
    _builder.newLineIfNotEmpty();
    {
      EList<HiPEPatternInvocation> _patternInvocations = b.getPatternInvocations();
      for(final HiPEPatternInvocation pi : _patternInvocations) {
        String _separator = IbexHiPEPlantUMLGenerator.separator();
        String _plus = (prefix + _separator);
        int _plusPlus = j++;
        String _plus_1 = (_plus + Integer.valueOf(_plusPlus));
        String _separator_1 = IbexHiPEPlantUMLGenerator.separator();
        String subPrefix = (_plus_1 + _separator_1);
        _builder.newLineIfNotEmpty();
        String _visualisePatternBody = IbexHiPEPlantUMLGenerator.visualisePatternBody(pi.getInvokedPattern(), subPrefix);
        _builder.append(_visualisePatternBody);
        _builder.newLineIfNotEmpty();
        {
          final Function1<HiPENode, Boolean> _function = (HiPENode n) -> {
            boolean _isLocal = n.isLocal();
            return Boolean.valueOf((!_isLocal));
          };
          Iterable<HiPENode> _filter = IterableExtensions.<HiPENode>filter(pi.getInvokedPattern().getNodes(), _function);
          for(final HiPENode param : _filter) {
            {
              boolean _isPositive = pi.isPositive();
              if (_isPositive) {
                CharSequence _identifierFor = IbexHiPEPlantUMLGenerator.identifierFor(param, b, prefix);
                _builder.append(_identifierFor);
                _builder.append(" #--#");
                _builder.newLineIfNotEmpty();
              } else {
                _builder.append("namespace ");
                _builder.append(subPrefix);
                String _name = pi.getInvokedPattern().getName();
                _builder.append(_name);
                _builder.append(" #DDDDDD {");
                _builder.newLineIfNotEmpty();
                CharSequence _identifierFor_1 = IbexHiPEPlantUMLGenerator.identifierFor(param, b, prefix);
                _builder.append(_identifierFor_1);
                _builder.append(" #..#");
                _builder.newLineIfNotEmpty();
                _builder.append("}");
                _builder.newLine();
              }
            }
          }
        }
      }
    }
    return _builder.toString();
  }
  
  private static String visualiseSymbolicParameters(final HiPEPattern p, final String prefix) {
    StringConcatenation _builder = new StringConcatenation();
    {
      final Function1<HiPENode, Boolean> _function = (HiPENode n) -> {
        boolean _isLocal = n.isLocal();
        return Boolean.valueOf((!_isLocal));
      };
      Iterable<HiPENode> _filter = IterableExtensions.<HiPENode>filter(p.getNodes(), _function);
      boolean _hasElements = false;
      for(final HiPENode v : _filter) {
        if (!_hasElements) {
          _hasElements = true;
        } else {
          _builder.appendImmediate("\n", "");
        }
        _builder.append("class ");
        CharSequence _identifierFor = IbexHiPEPlantUMLGenerator.identifierFor(v, p, prefix);
        _builder.append(_identifierFor);
        _builder.append("<< (V,#FF7700)>>");
        _builder.newLineIfNotEmpty();
      }
    }
    return _builder.toString();
  }
  
  private static String visualiseIsolatedPatternBody(final HiPEPattern b, final String prefix) {
    StringConcatenation _builder = new StringConcatenation();
    String _visualiseSymbolicParameters = IbexHiPEPlantUMLGenerator.visualiseSymbolicParameters(b, prefix);
    _builder.append(_visualiseSymbolicParameters);
    _builder.newLineIfNotEmpty();
    {
      final Function1<HiPENode, Boolean> _function = (HiPENode n) -> {
        return Boolean.valueOf(n.isLocal());
      };
      Iterable<HiPENode> _filter = IterableExtensions.<HiPENode>filter(b.getNodes(), _function);
      boolean _hasElements = false;
      for(final HiPENode v : _filter) {
        if (!_hasElements) {
          _hasElements = true;
        } else {
          _builder.appendImmediate("\n", "");
        }
        _builder.append("class ");
        CharSequence _identifierFor = IbexHiPEPlantUMLGenerator.identifierFor(v, b, prefix);
        _builder.append(_identifierFor);
        _builder.append("<< (L,#B0D8F0)>>");
        _builder.newLineIfNotEmpty();
      }
    }
    {
      EList<HiPEEdge> _edges = b.getEdges();
      for(final HiPEEdge ref : _edges) {
        CharSequence _identifierFor_1 = IbexHiPEPlantUMLGenerator.identifierFor(ref.getSource(), b, prefix);
        _builder.append(_identifierFor_1);
        _builder.append(" --> ");
        CharSequence _identifierFor_2 = IbexHiPEPlantUMLGenerator.identifierFor(ref.getTarget(), b, prefix);
        _builder.append(_identifierFor_2);
        _builder.newLineIfNotEmpty();
      }
    }
    return _builder.toString();
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
