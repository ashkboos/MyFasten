package eu.fasten.core.data;

import com.google.common.collect.BiMap;
import eu.fasten.core.data.callableindex.SourceCallSites;
import java.util.EnumMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class WholeProgramJavaCallGraph extends PartialJavaCallGraph{

    public final Map<String, String> type2Jar;

    public WholeProgramJavaCallGraph(PartialJavaCallGraph pcg, Map<String, String> type2jar){
        super(pcg.forge, pcg.product, pcg.version, pcg.timestamp, pcg.cgGenerator,
            pcg.classHierarchy, pcg.graph);
        this.type2Jar = type2jar;
    }
    public WholeProgramJavaCallGraph(String forge, String product,
                                     String version, long timestamp,
                                     String cgGenerator,
                                     EnumMap<JavaScope, Map<String, JavaType>> classHierarchy,
                                     SourceCallSites sourceCallSites,
                                     Map<String, String> type2jar) {
        super(forge, product, version, timestamp, cgGenerator, classHierarchy, sourceCallSites);
        this.type2Jar = type2jar;
    }

    public WholeProgramJavaCallGraph(String forge, String product,
                                     String version, long timestamp,
                                     String cgGenerator,
                                     EnumMap<JavaScope, Map<String, JavaType>> classHierarchy,
                                     JavaGraph graph,
                                     Map<String, String> type2jar) {
        super(forge, product, version, timestamp, cgGenerator, classHierarchy, graph);
        this.type2Jar = type2jar;
    }

    public WholeProgramJavaCallGraph(JSONObject json,
                                     Map<String, String> type2jar) throws JSONException {
        super(json);
        this.type2Jar = type2jar;
    }

    @Override
    public BiMap<Long, String> mapOfFullURIStrings() {
        return super.mapOfFullURIStrings(type2Jar);
    }
}
