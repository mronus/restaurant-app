package com.eatwithava;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.codegen.GenerationTool;
import org.jooq.impl.DSL;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Property;
import org.jooq.meta.jaxb.Target;

import com.eatwithava.jooq.tables.records.RestaurantsRecord;

import static com.eatwithava.jooq.tables.Restaurants.RESTAURANTS;
import static java.time.temporal.ChronoField.MINUTE_OF_DAY;
import static com.eatwithava.jooq.tables.OpenHours.OPEN_HOURS;
import static com.eatwithava.jooq.tables.MenuItems.MENU_ITEMS;

public class JooqRestaurantService {

    private final SQLRestaurantService sqlRestaurantService = new SQLRestaurantService();

    /**
     *
     * TODO: Implement me
     *
     *  NOTE:  This method should have the same logic as SQLRestaurantService.getOpenRestaurants, but should use the Jooq SQL DSL:
     *
     *  https://www.jooq.org/doc/3.13/manual-single-page/#select-statement
     *
     *
     */
    public List<RestaurantsRecord> getOpenRestaurants(final DayOfWeek dayOfWeek, final LocalTime localTime) throws SQLException {
        final String dayOfWeekString = dayOfWeek.toString();
        final Integer minuteOfDay    = localTime.get(MINUTE_OF_DAY);

        return withDSLContext(create -> {
            return create
                    .select(RESTAURANTS.ID, RESTAURANTS.NAME)
                    .from(RESTAURANTS)
                    .rightJoin(OPEN_HOURS).on(RESTAURANTS.ID.eq(OPEN_HOURS.RESTAURANT_ID))
                    .where(
                            OPEN_HOURS.DAY_OF_WEEK.eq(dayOfWeekString)
                            .and(
                                    OPEN_HOURS.START_TIME_MINUTE_OF_DAY.lt(OPEN_HOURS.END_TIME_MINUTE_OF_DAY)
                                    .and(OPEN_HOURS.START_TIME_MINUTE_OF_DAY.lt(minuteOfDay).and(OPEN_HOURS.END_TIME_MINUTE_OF_DAY.gt(minuteOfDay)))

                            )
                            .or(
                                    OPEN_HOURS.START_TIME_MINUTE_OF_DAY.gt(OPEN_HOURS.END_TIME_MINUTE_OF_DAY)
                                    .and(OPEN_HOURS.START_TIME_MINUTE_OF_DAY.lt(minuteOfDay).or(OPEN_HOURS.END_TIME_MINUTE_OF_DAY.gt(minuteOfDay)))
                            )
                    )
                    .fetchInto(RESTAURANTS);
        });
    }

    /**
     *
     *  TODO: Implement me
     *
     *  NOTE:  This method should have the same logic as SQLRestaurantService.getRestaurantsWithMenuOfSizeGreaterThanOrEqualTo, but should use the Jooq SQL DSL:
     *
     *  https://www.jooq.org/doc/3.13/manual-single-page/#select-statement
     *
     */
    public List<RestaurantsRecord> getRestaurantsWithMenuOfSizeGreaterThanOrEqualTo(final Integer menuSize) throws SQLException {
        return withDSLContext(create -> {
            return create
                    .select(RESTAURANTS.ID, RESTAURANTS.NAME)
                    .from(RESTAURANTS)
                    .rightJoin(MENU_ITEMS).on(RESTAURANTS.ID.eq(MENU_ITEMS.RESTAURANT_ID))
                    .groupBy(RESTAURANTS.ID)
                    .having(DSL.count().ge(menuSize))
                    .fetchInto(RESTAURANTS);
        });
    }

    public <T> T withDSLContext(final Function<DSLContext, T> function) throws SQLException {
        return sqlRestaurantService.runFunctionOnConnection(connection -> {
            final DSLContext create = DSL.using(connection, SQLDialect.H2);
            return function.apply(create);
        });
    }

    // NOTE: Ideally we should use the maven plugin to generate the Jooq classes
    public static void main (final String [] args) throws Exception {
        final SQLRestaurantService sqlRestaurantService = new SQLRestaurantService();
        sqlRestaurantService.initializeDatabase();

        Configuration configuration = new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver("org.h2.Driver")
                        .withUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;DB_CLOSE_ON_EXIT=false")
                        .withUser("sa")
                        .withPassword("sa"))
                .withGenerator(new Generator()
                        .withDatabase(new Database()
                                .withName("org.jooq.meta.h2.H2Database")
                                .withIncludes(".*")
                                .withExcludes("")
                                .withInputSchema("PUBLIC")
                                .withProperties(new Property()
                                        .withKey("defaultNameCase")
                                        .withValue("as_is")
                                )
                        )

                        .withTarget(new Target()
                                .withPackageName("com.eatwithava.jooq")
                                .withDirectory("src/main/java/")));

        GenerationTool.generate(configuration);
    }

}
