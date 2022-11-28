package eu.fasten.analyzer.dummyplugin;
import eu.fasten.core.plugins.KafkaPlugin;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public class DummyPlugin extends Plugin {
    public DummyPlugin (PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class DummyPluginExtension implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(DummyPlugin.class.getName());
        private List<String> consumeTopics = new LinkedList<>(Collections.singletonList("dummytopic"));
        private LinkedList<String> workingSet = new LinkedList<>();
        private boolean processedWorkingSet = false;

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(consumeTopics);
        }

        @Override
        public void setTopics(List<String> consumeTopics) {
            this.consumeTopics = consumeTopics;
        }

        @Override
        public void consume(String record) {
            logger.info("Processing" + record);

            if (workingSet.isEmpty() && !processedWorkingSet) {
                IntStream.range(0, 5).forEachOrdered(n -> {
                    workingSet.add("Item " + n);
                });
                processedWorkingSet = true;
            }
        }

        @Override
        public Optional<String> produce() {
            var json = new JSONObject();
            json.put("dummy", "dummy");
            return Optional.of(json.toString());
        }

        @Override
        public String getOutputPath() {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public String description() {
            return null;
        }

        @Override
        public String version() {
            return null;
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public Exception getPluginError() {
            return null;
        }

        @Override
        public void freeResource() {

        }
    }
}