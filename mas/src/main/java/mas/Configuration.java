package mas;

import rinde.sim.core.Simulator;
import rinde.sim.pdptw.common.AddParcelEvent;
import rinde.sim.pdptw.common.AddVehicleEvent;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.experiment.DefaultMASConfiguration;

import com.google.common.base.Optional;

public class Configuration extends DefaultMASConfiguration {
    final boolean smart;

    public Configuration(final boolean smart) {
        this.smart = smart;
    }

    @Override
    public Optional<? extends Creator<AddParcelEvent>> getParcelCreator() {
        return Optional.of(new Creator<AddParcelEvent>() {
            @Override
            public boolean create(Simulator sim, AddParcelEvent event) {
                // all parcels are accepted by default
                return sim.register(new DefaultParcel(event.parcelDTO));
            }
        });
    }

    @Override
    public Creator<AddVehicleEvent> getVehicleCreator() {
        Creator<AddVehicleEvent> creator;
        if (smart)
            creator = new Creator<AddVehicleEvent>() {
                @Override
                public boolean create(Simulator sim, AddVehicleEvent event) {
                    return sim.register(new SmartVehicle(event.vehicleDTO));
                }
            };
        else
            creator = new Creator<AddVehicleEvent>() {
                @Override
                public boolean create(Simulator sim, AddVehicleEvent event) {
                    return sim.register(new GreedyVehicle(event.vehicleDTO));
                }
            };
        return creator;
    }

}
