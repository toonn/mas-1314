import json
import collections

f = open('Gallconfigurations.json','r')
experimentInput = json.loads(f.read())
f.close()
g = open('Ggreedy.json','r')
experimentInput2 = json.loads(g.read())
g.close()


experiment = collections.defaultdict(dict)
for resource, configurations in experimentInput.iteritems():
    for configuration, statistics in configurations.iteritems():
        experiment[configuration][resource] = statistics
experiment2 = collections.defaultdict(dict)
for resource, configurations in experimentInput2.iteritems():
    for configuration, statistics in configurations.iteritems():
        experiment2[configuration][resource] = statistics

for key in experimentInput.iterkeys():
    experimentInput[key].update(experimentInput2[key])

h = open('Gallconfigurations_correct.json', 'w')
h.write(json.dumps(experimentInput))
h.close()
