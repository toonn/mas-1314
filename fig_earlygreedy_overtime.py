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
            'overTime' : 'Comparison of amount of cumulative overtime',
            'gTardiness' :
                'Tardiness in minutes according to Gendreau obj. function',
            'gOverTime' :
                'Overtime in minutes according to Gendreau obj. function',
            'gTravelTime' :
                'Traveltime in minutes according to Gendreau obj.function',
            'gCost' : 'Computed value of the Gendreau obj. function'}

for arg in ['Gallconfigurations_correct.json']:
    f = open(arg,'r')
    experimentInput = json.loads(f.read())
    f.close()
    
    experiment = collections.defaultdict(dict)
    for resource, configurations in experimentInput.iteritems():
        for configuration, statistics in configurations.iteritems():
            experiment[configuration][resource] = statistics


    opacity = 0.7
    lighten = 0.3
    
    nr_configurations = 6
    nr_scenarios = 3
    nr_resources = len(experiment.itervalues().next().keys()) / nr_scenarios

    spectral = cm.ScalarMappable(norm=colors.Normalize(vmin=-1,
                                                vmax=nr_configurations),
                                    cmap='spectral')

    resource_index = np.arange(nr_scenarios * nr_resources)
    scenario_index = np.arange(nr_scenarios) 
    bar_width = 1.0/(nr_configurations + 1)

    for var in ['gOverTime']:
        for conf_i, config in enumerate(
                ['bestfuture simple defaults',
                    'bestfuture trivial',
                    'early simple',
                    'early trivial',
                    'greedy',
                    'greedy global']):
            for res_i in xrange(0, nr_scenarios*nr_resources, 1):
                statistics = []
                for resource in [sorted(
                        experiment[config].iterkeys())[res_i]]:
                    statistics.append(experiment[config][resource][var])

                if res_i == 0:
                    label = config
                else:
                    label = '_nolegend_'

                plt.bar(res_i + conf_i*bar_width,
                        statistics,
                        bar_width,
                        alpha=opacity - res_i%2 * lighten,
                        color=spectral.to_rgba(conf_i),
                        label=label)
        
        plt.xlabel('Gendreau instance')
        plt.ylabel('Overtime')
        plt.title(titles[var])
        plt.xticks(resource_index + bar_width * nr_configurations / 2,
                [res[11:] for res in
                    sorted(experiment.itervalues().next().keys())],
                rotation = 30, fontsize=7)
        plt.subplots_adjust(top=.95, bottom=.16, left=.1, right=.99)
        plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.13),
                ncol=4, prop={'size' : 6})
        
        plt.savefig('plots/' + 'latex_earlygreedy_Overtime' + '.pdf')
        plt.clf()
