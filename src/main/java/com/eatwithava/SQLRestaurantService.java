package com.eatwithava;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.h2.jdbcx.JdbcDataSource;

import com.eatwithava.sql.RestaurantRecord;

import static java.time.temporal.ChronoField.MINUTE_OF_DAY;


public class SQLRestaurantService {


    /**
     *
     *  TODO:  Implement Me
     *
     *  Read the schema from src/main/resources/schema.sql
     *
     *  Write a prepared SQL statement (with safe variable replacement) which returns all the restaurants that are open for the given DayOfWeek and LocalTime
     *
     *  Using the same open logic from CSVRestaurantService.getOpenRestaurants
     *
     */
    public List<RestaurantRecord> getOpenRestaurants(final DayOfWeek dayOfWeek, final LocalTime localTime) throws SQLException {
        final String dayOfWeekString = dayOfWeek.toString();
        final Integer minuteOfDay    = localTime.get(MINUTE_OF_DAY);

        final String query = String.join("\n",
                 "select r.id, r.name from restaurants r"
                , "right join open_hours o on r.id = o.restaurant_id"
                , "where o.day_of_week = ?"
                , "and ("
                , "(o.start_time_minute_of_day < o.end_time_minute_of_day and o.start_time_minute_of_day < ? and o.end_time_minute_of_day > ?)"
                , "or (o.start_time_minute_of_day > o.end_time_minute_of_day and (o.start_time_minute_of_day < ? or o.end_time_minute_of_day > ?))"
                , ")"
        );

        return runQueryAndParseRestaurants(query, dayOfWeekString, minuteOfDay, minuteOfDay, minuteOfDay, minuteOfDay);
    }

    /**
     *
     *  TODO:  Implement Me
     *
     *  Read the schema from src/main/resources/schema.sql
     *
     *  Write a prepared SQL statement (with safe variable replacement)  which returns all the restaurants which have at least menuSize number of menu_items
     *
     */
    public List<RestaurantRecord> getRestaurantsWithMenuOfSizeGreaterThanOrEqualTo(final Integer menuSize) throws SQLException {


        final String query = String.join("\n",
                "select r.id, r.name from restaurants r"
                , "right join menu_items m on r.id = m.restaurant_id"
                , "group by r.id"
                , "having count(r.id) >= ?"
        );

        return runQueryAndParseRestaurants(query, menuSize);
    }


    public List<RestaurantRecord> getAllRestaurantRecordsWithIds(final Collection<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        final String inList = StringUtils.join(ids.stream().map(x -> x.toString()).collect(Collectors.toList()), ",");
        return runQueryAndParseRestaurants("select * from restaurants where id in (" + inList + ")");
    }

    public void initializeDatabase() throws Exception {
        runOnStatement(statement-> {
            final String schemaSql = ResourceLoader.readResourceAsString("schema.sql");
            statement.execute(schemaSql);
            System.out.println("Done creating schema");

            boolean hasData = false;
            final ResultSet countRS = statement.executeQuery("select count(*) as count from restaurants");
            while(countRS.next()) {
                hasData = countRS.getInt("count") > 0;
            }

            if (hasData) {
                System.out.println("No need to insert data");
            } else {
                final String dataSql = ResourceLoader.readResourceAsString("data.sql");
                statement.execute(dataSql);
                System.out.println("Done inserting data");
            }
        });
    }

    @FunctionalInterface
    public interface ExceptionThrowingConsumer<T, E extends Exception> {
        void accept(final T t) throws E;
    }

    @FunctionalInterface
    public interface ExceptionThrowingFunction<S, T, E extends Exception> {
        T apply(final S s) throws E;
    }


    public <E extends Exception> void runOnStatement(final ExceptionThrowingConsumer<Statement, E> consumer) throws E, SQLException {
        try (
                final Connection connection = createConnection();
                final Statement statement = connection.createStatement();
        ) {
            consumer.accept(statement);
        }
    }

    public <E extends Exception> void runOnConnection(final ExceptionThrowingConsumer<Connection, E> consumer) throws E, SQLException {
        try (
                final Connection connection = createConnection();
        ) {
            consumer.accept(connection);
        }
    }

    public <T, E extends Exception> T runFunctionOnConnection(final ExceptionThrowingFunction<Connection, T, E> function) throws E, SQLException {
        try (
                final Connection connection = createConnection();
        ) {
            return function.apply(connection);
        }
    }

    private List<RestaurantRecord> runQueryAndParseRestaurants(final String query, final Object... parameters) throws SQLException {
        final List<RestaurantRecord> results = new ArrayList<>();
         runOnConnection(statement-> {

             final PreparedStatement preparedStatement = statement.prepareStatement(query);
             for (int i = 1; i <= parameters.length; i++) {
                 preparedStatement.setObject(i, parameters[i-1]);
             }

             final ResultSet rs = preparedStatement.executeQuery();
             while (rs.next()) {
                 results.add(new RestaurantRecord(rs.getLong("id"), rs.getString("name")));
             }
         });

         return results;
    }

    private List<RestaurantRecord> runQueryAndParseRestaurants(final String query) throws SQLException {
        final List<RestaurantRecord> results = new ArrayList<>();
         runOnStatement(statement-> {
            final ResultSet rs = statement.executeQuery(query);
            while (rs.next()) {
                results.add(new RestaurantRecord(rs.getLong("id"), rs.getString("name")));
            }
        });

         return results;
    }


   private Connection createConnection() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("sa");
        return ds.getConnection();
    }
}
