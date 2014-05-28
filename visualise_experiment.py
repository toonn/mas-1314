import sys
import json
import numpy as np
import matplotlib.pyplot as plt
import collections
import matplotlib.cm as cm
import matplotlib.colors as colors

titles = { 'totalDistance' : 'Comparison of total distance traveled',
            'pickupTardiness' : 'Comparison of amount of pickup tardiness',
            'deliveryTardiness' : 'Comparison of amount of delivery tardiness',
            'simulationTime' : 'Comparison of time needed to complete scenario',
            'overTime' : 'Comparison of amount of cumulative overtime'}

for arg in sys.argv[1:]:
    f = open(arg,'r')
    experimentInput = json.loads(f.read())
    f.close()
    
    experiment = collections.defaultdict(dict)
    for resource, configurations in experimentInput.iteritems():
        for configuration, statistics in configurations.iteritems():
            experiment[configuration][resource] = statistics


    opacity = 0.5
    
    nr_configurations = len(experiment.keys())
    nr_resources = len(experiment.itervalues().next().keys())

    spectral = cm.ScalarMappable(norm=colors.Normalize(vmin=-1,
                                                vmax=nr_configurations),
                                    cmap='spectral')

    resource_index = np.arange(nr_resources) 
    bar_width = 1.0/(nr_configurations + 1)

    for var in sorted(titles.iterkeys()):
        for index, config in enumerate(sorted(experiment.iterkeys())):
            totalDistances = []
            for resource in sorted(experiment[config].iterkeys()):
                totalDistances.append(experiment[config][resource][var])
            plt.bar(resource_index + index*bar_width,
                    totalDistances,
                    bar_width,
                    alpha=opacity,
                    color=spectral.to_rgba(index),
                    label=config)
        
        plt.xlabel('Gendreau scenario')
        plt.ylabel(var)
        plt.title(titles[var])
        plt.xticks(resource_index + bar_width * nr_configurations / 2,
                [res[11:] for res in
                    sorted(experiment.itervalues().next().keys())],
                rotation = 45)
        plt.legend()
        
        plt.tight_layout()
        plt.savefig('plots/' + arg[:-5] + '_' + var + '.png')
        plt.clf()
