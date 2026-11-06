package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.entity.DayPlan;
import com.tripplanner.backend.entity.HotelSuggestion;
import com.tripplanner.backend.entity.Trip;

import java.util.List;

/** Detached, fully initialized data needed to build a trip response outside a transaction. */
record TripDataBundle(Trip trip, List<DayPlan> dayPlans, List<HotelSuggestion> hotels) {}
