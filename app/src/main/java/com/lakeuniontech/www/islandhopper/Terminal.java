package com.lakeuniontech.www.islandhopper;

enum Terminal {
    // Display Name, Washington Ferries Terminal Id, LatLong of Terminal
    ANACORTES("Anacortes", 1, "48.502654, -122.679297"),
    FRIDAY_HARBOR("Friday Harbor", 10, "48.534236, -123.015236"),
    LOPEZ("Lopez", 13, "48.570405, -122.883685"),
    ORCAS("Orcas", 15, "48.598541, -122.945827"),
    SHAW("Shaw", 18, "48.584230, -122.929721");

    public final String name;
    public final Integer id;
    public final String latLong;

    Terminal(String name, Integer id, String latLong) {
        this.name = name;
        this.id = id;
        this.latLong = latLong;
    }

    @Override
    public String toString() {
        return name;
    }

    // Return the arrival (destination) terminal for this terminal
    public Terminal getArriveTerminal() {
        if (this == Terminal.ANACORTES)
            return Terminal.ORCAS;
        else
            return Terminal.ANACORTES;
    }
}
