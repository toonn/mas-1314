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


    opacity = 0.6
    lighten = 0.3
    
    nr_configurations = len(experiment.keys())
    nr_scenarios = 3
    nr_resources = len(experiment.itervalues().next().keys()) / nr_scenarios

    spectral = cm.ScalarMappable(norm=colors.Normalize(vmin=-1,
                                                vmax=nr_configurations),
                                    cmap='spectral')

    resource_index = np.arange(nr_scenarios * nr_resources)
    scenario_index = np.arange(nr_scenarios) 
    bar_width = 1.0/(nr_configurations + 1)

    for var in sorted(titles.iterkeys()):
        for index, config in enumerate(sorted(experiment.iterkeys())):
            for res_i in xrange(0, nr_resources*nr_scenarios, 3):
                statistics = []
                for resource in sorted(
                        experiment[config].iterkeys())[res_i:res_i+3]:
                    statistics.append(experiment[config][resource][var])

                if res_i == 0:
                    label = config
                else:
                    label = '_nolegend_'

                plt.bar(scenario_index + res_i + index*bar_width,
                        statistics,
                        bar_width,
                        alpha=opacity - res_i%2 * lighten,
                        color=spectral.to_rgba(index),
                        label=label)
        
        plt.xlabel('Gendreau scenario')
        plt.ylabel(var)
        plt.title(titles[var])
        plt.xticks(resource_index + bar_width * nr_configurations / 2,
                [res[11:] for res in
                    sorted(experiment.itervalues().next().keys())],
                rotation = 30, fontsize=7)
        plt.subplots_adjust(top=.95, bottom=.22, left=.05, right=.99)
        plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.15),
                ncol=4, prop={'size' : 6})
        
        plt.savefig('plots/' + arg[:-5] + '_' + var + '.png')
        plt.clf()

