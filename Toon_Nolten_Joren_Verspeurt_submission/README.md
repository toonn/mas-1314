Running the experiment
======================

We have included a jar file which will run an experiment in interactive mode,
with a gui and requiring user interaction.
This is used as follows:

   $ java -jar T_Nolten_J_Verspeurt_mas.jar

If you run each of the fifteen simulations completely (letting them finish
before closing the window), this will produce a json file containing the
results of the experiment.
A bar chart can then be plotted by running:

    $ python visualise_experiment.py experiment.json

This will create one plot for every variable in the experiment.

The jar file executes only a select subset (5) of our configurations,
and that for only three Gendreau instances, because otherwise it would take
too long to run. (Our entire experiment takes more than 10 hours.)

If more configurations need to be tested, you will have to import the 'mas'
folder as a maven project, maven should download all the required libraries.
Then you can edit the class Configuration, toggleing the configurations of
interest, and then running SmartVSGreedy as a java application.
