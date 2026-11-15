-- V10: reference data required in every environment (deterministic, idempotent through Flyway versioning).
-- Nutrition values are approximate per-100 g estimates for a synthetic local catalogue (BA §2.3 Table 2.4,
-- R9: synthetic meal/activity catalogue; LEG-6: values are estimates). MET values follow the magnitude of
-- the 2024 Adult Compendium of Physical Activities [9] and are estimates, not medical advice.
-- Demo users and diary/plan data are NOT part of migrations - see infra/postgres/seed/demo_data.sql.

INSERT INTO goal_type (code, name) VALUES
    ('LOSE_WEIGHT',     'Lose weight'),
    ('MAINTAIN_WEIGHT', 'Maintain weight'),
    ('GAIN_WEIGHT',     'Gain weight / build muscle');

INSERT INTO meal_slot (code, name, sort_order) VALUES
    ('BREAKFAST', 'Breakfast', 1),
    ('LUNCH',     'Lunch',     2),
    ('DINNER',    'Dinner',    3),
    ('SNACK',     'Snack',     4);

INSERT INTO equipment (code, name) VALUES
    ('DUMBBELLS',       'Dumbbells'),
    ('KETTLEBELL',      'Kettlebell'),
    ('RESISTANCE_BAND', 'Resistance band'),
    ('YOGA_MAT',        'Yoga mat'),
    ('STATIONARY_BIKE', 'Stationary bike'),
    ('JUMP_ROPE',       'Jump rope'),
    ('ROWING_MACHINE',  'Rowing machine'),
    ('POOL',            'Swimming pool');

INSERT INTO ingredient (code, name) VALUES
    ('OAT',        'Oats'),
    ('GLUTEN',     'Gluten'),
    ('WHEAT',      'Wheat'),
    ('BANANA',     'Banana'),
    ('APPLE',      'Apple'),
    ('MILK',       'Milk / dairy'),
    ('EGG',        'Egg'),
    ('CHICKEN',    'Chicken'),
    ('BEEF',       'Beef'),
    ('PORK',       'Pork'),
    ('FISH',       'Fish'),
    ('RICE',       'Rice'),
    ('BUCKWHEAT',  'Buckwheat'),
    ('POTATO',     'Potato'),
    ('BROCCOLI',   'Broccoli'),
    ('VEGETABLES', 'Mixed vegetables'),
    ('TOMATO',     'Tomato'),
    ('LENTIL',     'Lentils'),
    ('SOY',        'Soy'),
    ('PEANUT',     'Peanut'),
    ('TREE_NUT',   'Tree nuts');

INSERT INTO recovery_setting (id, kcal_deviation_threshold) VALUES (true, 200);

INSERT INTO consent_document (consent_type, version, published_on, summary) VALUES
    ('PRIVACY_NOTICE',      '1.0', DATE '2026-11-01', 'VEYA privacy notice: purposes, legal bases (Art. 6(1)(b)), retention and user rights.'),
    ('HEALTH_DATA_CONSENT', '1.0', DATE '2026-11-01', 'Explicit consent to process nutrition and activity records that may be health data (Art. 9(2)(a)).');

-- Local food catalogue -------------------------------------------------------------------------
INSERT INTO food_item (name, source, kcal_per_100g, protein_per_100g, fat_per_100g, carbs_per_100g) VALUES
    ('Rolled oats',            'LOCAL', 389.0, 16.9,   6.9, 66.3),
    ('Banana',                 'LOCAL',  89.0,  1.1,   0.3, 22.8),
    ('Milk 2.5%',              'LOCAL',  52.0,  3.0,   2.5,  4.7),
    ('Chicken breast, cooked', 'LOCAL', 165.0, 31.0,   3.6,  0.0),
    ('White rice, cooked',     'LOCAL', 130.0,  2.7,   0.3, 28.2),
    ('Broccoli',               'LOCAL',  34.0,  2.8,   0.4,  6.6),
    ('Salmon, baked',          'LOCAL', 206.0, 22.1,  12.4,  0.0),
    ('Potatoes, boiled',       'LOCAL',  87.0,  1.9,   0.1, 20.1),
    ('Tofu',                   'LOCAL',  76.0,  8.1,   4.8,  1.9),
    ('Mixed vegetables',       'LOCAL',  65.0,  2.6,   0.3, 13.1),
    ('Lentils, cooked',        'LOCAL', 116.0,  9.0,   0.4, 20.1),
    ('Eggs',                   'LOCAL', 143.0, 12.6,   9.5,  0.7),
    ('Whole wheat bread',      'LOCAL', 247.0, 13.0,   3.4, 41.0),
    ('Peanut butter',          'LOCAL', 588.0, 25.1,  50.4, 20.0),
    ('Greek yogurt',           'LOCAL',  97.0,  9.0,   5.0,  3.9),
    ('Apple',                  'LOCAL',  52.0,  0.3,   0.2, 13.8),
    ('Pasta, cooked',          'LOCAL', 158.0,  5.8,   0.9, 30.9),
    ('Tomato sauce',           'LOCAL',  29.0,  1.4,   0.2,  6.0),
    ('Beef, lean, cooked',     'LOCAL', 217.0, 26.1,  11.8,  0.0),
    ('Olive oil',              'LOCAL', 884.0,  0.0, 100.0,  0.0),
    ('Cottage cheese',         'LOCAL',  98.0, 11.1,   4.3,  3.4),
    ('Buckwheat, cooked',      'LOCAL',  92.0,  3.4,   0.6, 19.9),
    ('Pork, cooked',           'LOCAL', 242.0, 27.0,  14.0,  0.0),
    ('Almonds',                'LOCAL', 579.0, 21.2,  49.9, 21.6);

INSERT INTO food_item_ingredient (food_item_id, ingredient_code)
SELECT f.id, x.ingredient_code
FROM (VALUES
    ('Rolled oats', 'OAT'), ('Rolled oats', 'GLUTEN'),
    ('Banana', 'BANANA'),
    ('Milk 2.5%', 'MILK'),
    ('Chicken breast, cooked', 'CHICKEN'),
    ('White rice, cooked', 'RICE'),
    ('Broccoli', 'BROCCOLI'),
    ('Salmon, baked', 'FISH'),
    ('Potatoes, boiled', 'POTATO'),
    ('Tofu', 'SOY'),
    ('Mixed vegetables', 'VEGETABLES'),
    ('Lentils, cooked', 'LENTIL'),
    ('Eggs', 'EGG'),
    ('Whole wheat bread', 'WHEAT'), ('Whole wheat bread', 'GLUTEN'),
    ('Peanut butter', 'PEANUT'),
    ('Greek yogurt', 'MILK'),
    ('Apple', 'APPLE'),
    ('Pasta, cooked', 'WHEAT'), ('Pasta, cooked', 'GLUTEN'),
    ('Tomato sauce', 'TOMATO'),
    ('Beef, lean, cooked', 'BEEF'),
    ('Cottage cheese', 'MILK'),
    ('Buckwheat, cooked', 'BUCKWHEAT'),
    ('Pork, cooked', 'PORK'),
    ('Almonds', 'TREE_NUT')
) AS x (food_name, ingredient_code)
JOIN food_item f ON f.name = x.food_name AND f.source = 'LOCAL';

-- Candidate meals -----------------------------------------------------------------------------
INSERT INTO meal (name, meal_slot_code, prep_time_min) VALUES
    ('Oatmeal with banana',         'BREAKFAST', 10),
    ('Scrambled eggs on toast',     'BREAKFAST', 10),
    ('Greek yogurt with apple',     'SNACK',      3),
    ('Chicken rice bowl',           'LUNCH',     20),
    ('Pasta bolognese',             'LUNCH',     30),
    ('Lentil soup',                 NULL,        35),
    ('Salmon with potatoes',        'DINNER',    30),
    ('Tofu vegetable stir-fry',     'DINNER',    20),
    ('Buckwheat with chicken',      'DINNER',    25),
    ('Pork with potatoes',          'DINNER',    35),
    ('Cottage cheese with almonds', 'SNACK',      3),
    ('Peanut butter toast',         'SNACK',      3);

INSERT INTO meal_component (meal_id, food_item_id, grams)
SELECT m.id, f.id, x.grams
FROM (VALUES
    ('Oatmeal with banana', 'Rolled oats', 60), ('Oatmeal with banana', 'Milk 2.5%', 200), ('Oatmeal with banana', 'Banana', 100),
    ('Scrambled eggs on toast', 'Eggs', 120), ('Scrambled eggs on toast', 'Whole wheat bread', 60),
    ('Greek yogurt with apple', 'Greek yogurt', 200), ('Greek yogurt with apple', 'Apple', 150),
    ('Chicken rice bowl', 'Chicken breast, cooked', 150), ('Chicken rice bowl', 'White rice, cooked', 200),
    ('Chicken rice bowl', 'Broccoli', 100), ('Chicken rice bowl', 'Olive oil', 10),
    ('Pasta bolognese', 'Pasta, cooked', 200), ('Pasta bolognese', 'Beef, lean, cooked', 100), ('Pasta bolognese', 'Tomato sauce', 120),
    ('Lentil soup', 'Lentils, cooked', 250), ('Lentil soup', 'Mixed vegetables', 150), ('Lentil soup', 'Olive oil', 10),
    ('Salmon with potatoes', 'Salmon, baked', 150), ('Salmon with potatoes', 'Potatoes, boiled', 250), ('Salmon with potatoes', 'Broccoli', 100),
    ('Tofu vegetable stir-fry', 'Tofu', 200), ('Tofu vegetable stir-fry', 'Mixed vegetables', 200),
    ('Tofu vegetable stir-fry', 'White rice, cooked', 150), ('Tofu vegetable stir-fry', 'Olive oil', 10),
    ('Buckwheat with chicken', 'Buckwheat, cooked', 200), ('Buckwheat with chicken', 'Chicken breast, cooked', 120),
    ('Buckwheat with chicken', 'Mixed vegetables', 100),
    ('Pork with potatoes', 'Pork, cooked', 150), ('Pork with potatoes', 'Potatoes, boiled', 250),
    ('Cottage cheese with almonds', 'Cottage cheese', 200), ('Cottage cheese with almonds', 'Almonds', 20),
    ('Peanut butter toast', 'Whole wheat bread', 60), ('Peanut butter toast', 'Peanut butter', 20)
) AS x (meal_name, food_name, grams)
JOIN meal m ON m.name = x.meal_name
JOIN food_item f ON f.name = x.food_name AND f.source = 'LOCAL';

-- Exercise catalogue ----------------------------------------------------------------------------
INSERT INTO exercise (name, met_value, source) VALUES
    ('Running, 10 km/h',            9.8, 'LOCAL'),
    ('Brisk walking',               4.3, 'LOCAL'),
    ('Bodyweight circuit',          8.0, 'LOCAL'),
    ('Stretching',                  2.3, 'LOCAL'),
    ('Dumbbell strength training',  5.0, 'LOCAL'),
    ('Kettlebell swings',           9.8, 'LOCAL'),
    ('Resistance band workout',     3.5, 'LOCAL'),
    ('Yoga',                        2.5, 'LOCAL'),
    ('Stationary cycling, moderate', 6.8, 'LOCAL'),
    ('Jump rope',                  11.8, 'LOCAL'),
    ('Rowing machine, moderate',    7.0, 'LOCAL'),
    ('Swimming, leisurely',         5.8, 'LOCAL');

INSERT INTO exercise_equipment (exercise_id, equipment_code)
SELECT e.id, x.equipment_code
FROM (VALUES
    ('Dumbbell strength training', 'DUMBBELLS'),
    ('Kettlebell swings', 'KETTLEBELL'),
    ('Resistance band workout', 'RESISTANCE_BAND'),
    ('Yoga', 'YOGA_MAT'),
    ('Stationary cycling, moderate', 'STATIONARY_BIKE'),
    ('Jump rope', 'JUMP_ROPE'),
    ('Rowing machine, moderate', 'ROWING_MACHINE'),
    ('Swimming, leisurely', 'POOL')
) AS x (exercise_name, equipment_code)
JOIN exercise e ON e.name = x.exercise_name AND e.source = 'LOCAL';
