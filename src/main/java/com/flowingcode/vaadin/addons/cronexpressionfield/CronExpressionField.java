/*-
 * #%L
 * Cron Expression Field Add-on
 * %%
 * Copyright (C) 2025 Flowing Code
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package com.flowingcode.vaadin.addons.cronexpressionfield;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import com.flowingcode.vaadin.addons.dayofweekselector.DayOfWeekSelector;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.internal.JsonSerializer;
import elemental.json.JsonObject;
import org.springframework.scheduling.support.CronExpression;
import it.burning.cron.CronExpressionDescriptor;
import it.burning.cron.CronExpressionParser.CronExpressionParseException;
import it.burning.cron.CronExpressionParser.Options;

/**
 * UI component for building cron expressions.
 *
 * @author Sofia Nuñez / Flowing Code
 */
@SuppressWarnings("serial")
@CssImport(value = "./styles/cron-expression-field-styles.css")
public class CronExpressionField extends CustomField<String> {

  /**
   * Default value used only when {@code inputExpressionTf} and {@code defaultExpression} are empty. Represents every
   * day at midnight.
   */
  private static final String DEFAULT_CRON = "0 0 0 * * *";
  private Locale locale;
  private String defaultExpression;
  private List<String> commonExpressionsList;
  private final ComboBox<LayoutOptions> cronExpressionLayoutCb = new ComboBox<>();
  private final VerticalLayout mainLayout = new VerticalLayout();
  private final TextField inputExpressionTf = new TextField();
  private final Div cronDescriptionDiv = new Div();
  private final Grid<LocalDateTime> grid = new Grid<>();
  private final Button nextDatesBtn = new Button();
  private final ComboBox<String> commonExpressionsCb = new ComboBox<>();

  protected enum Units {
    SECONDS, MINUTES, HOURS;
  }

  private CronExpressionFieldI18n i18n;
  private boolean helpEnabled;
  private boolean commonExpressionsVisible;
  private boolean cronInputEnabled;

  /** Creates a new instance of {@code CronExpressionField} */
  public CronExpressionField() {
    this.setClassName("fc-cron-expression-field");
    setI18n(null);
    configureLayout();
  }

  /**
   * Creates a new instance of {@code CronExpressionField} with a default expression.
   * 
   * @param defaultExpression a {@code String} representing the default cron expression to be set
   */
  public CronExpressionField(String defaultExpression) {
    this();
    setDefaultExpression(defaultExpression);
  }

  private void updateCronExpression(Map<Integer, String> cronValues) {
    String[] cronExpressionSplit =
        inputExpressionTf.getValue().isEmpty() ? DEFAULT_CRON.split(" ") : inputExpressionTf.getValue().split(" ");
    for (Integer i : cronValues.keySet()) {
      cronExpressionSplit[i] = cronValues.get(i);
    }
    inputExpressionTf.setValue(String.join(" ", cronExpressionSplit));
  }

  private void updateCronExpression(String cron) {
    if (validateCron(cron)) {
      inputExpressionTf.setValue(cron);
      updateDescription();
    } else {
      cronDescriptionDiv.setText(i18n.getDescriptionPlaceholder());
      inputExpressionTf.setInvalid(true);
    }
  }

  private void updateDescription() {
    if (validateCron(inputExpressionTf.getValue())) {
      cronDescriptionDiv.setText(CronExpressionDescriptor.getDescription(inputExpressionTf.getValue(), new Options() {
        {
          setUseJavaEeScheduleExpression(true);
          setLocale(locale == null ? Locale.ENGLISH : locale);
        }
      }));
    }
  }

  private boolean validateCron(String cron) {
    if (cron != null && !cron.isEmpty()) {
      try {
        CronExpressionDescriptor.getDescription(cron);
        return CronExpression.isValidExpression(cron);
      } catch (CronExpressionParseException | IllegalArgumentException e) {
        return false;
      }
    } else {
      return false;
    }
  }

  private void configureLayout() {
    inputExpressionTf.setValue(defaultExpression == null ? "" : defaultExpression);
    Button clearInputBtn = new Button(i18n.getClearBtn(), e -> {
      inputExpressionTf.setValue(defaultExpression == null ? "" : defaultExpression);
      resetUI();
      inputExpressionTf.setInvalid(false);
    });
    inputExpressionTf.setSuffixComponent(clearInputBtn);
    inputExpressionTf.addValueChangeListener(v -> {
      updateCronExpression(v.getValue());
      setModelValue(v.getValue(), true);
    });
    cronExpressionLayoutCb.setAllowCustomValue(false);
    cronExpressionLayoutCb.setItems(LayoutOptions.values());
    cronExpressionLayoutCb.setItemLabelGenerator(layout -> layoutOptionsTranslation(layout));
    cronExpressionLayoutCb.addValueChangeListener(v -> {
      switch (v.getValue()) {
        case MONTHLY:
          setMonthlyLayout();
          break;
        case ADVANCED:
          setAdvancedLayout();
          break;
        default:
          setDailyLayout();
          break;
      };
    });
    cronExpressionLayoutCb.setValue(LayoutOptions.ADVANCED);

    HorizontalLayout topLayout = new HorizontalLayout(cronExpressionLayoutCb, inputExpressionTf);

    clearInputBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
    inputExpressionTf.setSizeFull();
    cronExpressionLayoutCb.setWidth("40%");
    cronDescriptionDiv.setWidthFull();
    cronDescriptionDiv.setClassName("description-div");
    addClassName("fc-cron-expression-field");
    setMaxWidth("450px");

    mainLayout.addClassName("fc-cron-expression-field-main-layout");

    topLayout.setWidthFull();
    topLayout.setAlignItems(Alignment.END);
    topLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);

    if (helpEnabled) {
      inputExpressionTf.setTooltipText(i18n.getInputExpressionTooltip());
    }

    nextDatesBtn.setText(i18n.getShowNextDatesBtn());
    nextDatesBtn.addClickListener(e -> showNextDates());
    nextDatesBtn.setVisible(false);

    add(topLayout, mainLayout, new Span(i18n.getDescriptionLabel()), cronDescriptionDiv, nextDatesBtn);
    resetUI();
  }

  private String layoutOptionsTranslation(LayoutOptions option) {
    return i18n.getLayoutOptions().get(option);
  }

  private void resetUI() {
    switch (cronExpressionLayoutCb.getValue()) {
      case MONTHLY:
        setMonthlyLayout();
        break;
      case ADVANCED:
        setAdvancedLayout();
        break;
      default:
        setDailyLayout();
        break;
    };
  }

  private void setAdvancedLayout() {
    mainLayout.removeAll();
    inputExpressionTf.setValue(defaultExpression == null ? "" : defaultExpression);
    inputExpressionTf.setInvalid(false);
    if (grid != null) {
      remove(grid);
    }
    inputExpressionTf.setReadOnly(false);
    if (commonExpressionsVisible) {
      if (commonExpressionsList != null)
        commonExpressionsCb.setItems(commonExpressionsList);

      commonExpressionsCb.setItemLabelGenerator(i -> {
        String label = CronExpressionDescriptor.getDescription(i, new Options() {
          {
            setUseJavaEeScheduleExpression(true);
            setLocale(locale == null ? Locale.ENGLISH : locale);
          }
        });
        return label + " (" + i + ")";
      });

      commonExpressionsCb.addValueChangeListener(v -> updateCronExpression(commonExpressionsCb.getValue()));
      commonExpressionsCb.setWidthFull();
      commonExpressionsCb.addClassName("fc-common-expressions-combobox");
      commonExpressionsCb.setLabel(i18n.getCommonExpressionsLabel());
      commonExpressionsCb.setAllowCustomValue(false);
      mainLayout.add(commonExpressionsCb);
    }
  }

  private void setDailyLayout() {
    mainLayout.removeAll();
    inputExpressionTf.setValue(defaultExpression == null ? "" : defaultExpression);
    inputExpressionTf.setInvalid(false);

    if (grid != null) {
      remove(grid);
    }
    inputExpressionTf.setReadOnly(!cronInputEnabled);

    TimePicker startsAtTp = new TimePicker(i18n.getStartsAtLabel());
    startsAtTp.setStep(Duration.ofMinutes(30));
    startsAtTp.addValueChangeListener(v -> getStartsAtExpression(v.getValue()));

    IntegerField timeAmountField = new IntegerField(i18n.getRepeatEveryLabel());
    timeAmountField.setMin(0);
    ComboBox<Units> timeUnitCb = new ComboBox<>();
    timeUnitCb.setClassName("inline-combobox");
    timeUnitCb.setWidth("50%");
    timeUnitCb.setItems(Units.values());
    timeUnitCb.setItemLabelGenerator(i -> {
      return i18n.getUnits().get(i);
    });
    timeUnitCb.setValue(Units.SECONDS);
    timeUnitCb.addValueChangeListener(v -> {
      if (v.getValue().equals(Units.HOURS)) {
        timeAmountField.setMax(23);
      } else {
        timeAmountField.setMax(59);
      }
      if (timeAmountField.getValue() != null && !timeAmountField.isEmpty())
        getDailyExpression(v.getValue(), timeAmountField.getValue());
    });
    timeAmountField.addValueChangeListener(v -> {
      if (!timeAmountField.isInvalid() && !timeAmountField.isEmpty()) {
        getDailyExpression(timeUnitCb.getValue(), v.getValue());
      }
    });
    timeAmountField.addClassName("fc-daily-layout-time-amount");
    timeAmountField.setSuffixComponent(timeUnitCb);

    DayOfWeekSelector daySelector = new DayOfWeekSelector();
    daySelector.addValueChangeListener(v -> {
      getWeeklyExpression(daySelector.getValue());
    });

    if (helpEnabled) {
      timeAmountField.setTooltipText(i18n.getTimeAmountTooltip());
      daySelector.setTooltipText(i18n.getDaySelectorTooltip());
      startsAtTp.setTooltipText(i18n.getStartsAtTooltip());
    }
    FormLayout formLayout = new FormLayout(startsAtTp, timeAmountField);
    formLayout.setResponsiveSteps(new ResponsiveStep("0", 1), new ResponsiveStep("300px", 2));
    formLayout.setWidthFull();
    mainLayout.add(formLayout, daySelector);
  }

  private void setMonthlyLayout() {
    mainLayout.removeAll();
    inputExpressionTf.setValue(defaultExpression == null ? "" : defaultExpression);
    inputExpressionTf.setInvalid(false);
    if (grid != null) {
      remove(grid);
    }
    inputExpressionTf.setReadOnly(!cronInputEnabled);

    TimePicker startsAtTp = new TimePicker(i18n.getStartsAtLabel());
    startsAtTp.setStep(Duration.ofMinutes(30));
    startsAtTp.addValueChangeListener(v -> getStartsAtExpression(v.getValue()));
    startsAtTp.setWidth("35%");
    IntegerField dayOfMonthField = new IntegerField(i18n.getDayOfMonthLabel());
    dayOfMonthField.setValue(1);
    dayOfMonthField.setWidth("30%");
    IntegerField monthField = new IntegerField(i18n.getRepeatEveryLabel());
    monthField.setSuffixComponent(new Span(i18n.getMonth()));
    monthField.setValue(1);
    monthField.setStepButtonsVisible(true);
    monthField.setMin(1);
    monthField.setMax(11);
    monthField.addValueChangeListener(v -> {
      if (!monthField.isInvalid() && !dayOfMonthField.isInvalid()) {
        getMonthlyExpression(dayOfMonthField.getValue(), v.getValue());
      }
    });
    monthField.setWidth("35%");
    dayOfMonthField.addValueChangeListener(v -> {
      if (!monthField.isInvalid() && !dayOfMonthField.isInvalid()) {
        getMonthlyExpression(v.getValue(), monthField.getValue());
      }
    });
    if (helpEnabled) {
      dayOfMonthField.setTooltipText(i18n.getDayOfMonthTooltip());
      monthField.setTooltipText(i18n.getMonthFieldTooltip());
      startsAtTp.setTooltipText(i18n.getStartsAtTooltip());
    }
    HorizontalLayout layout = new HorizontalLayout(startsAtTp, dayOfMonthField, monthField);
    layout.setWidthFull();
    mainLayout.add(layout);
  }

  private void getStartsAtExpression(LocalTime startsAt) {
    if (inputExpressionTf.getValue().isEmpty()) {
      updateCronExpression(DEFAULT_CRON);
    }
    if (startsAt != null) {
      Map<Integer, String> values = new HashMap<>();
      values.put(0, String.valueOf(startsAt.getSecond()));
      values.put(1, String.valueOf(startsAt.getMinute()));
      values.put(2, String.valueOf(startsAt.getHour()));
      updateCronExpression(values);
    }
  }

  private void getMonthlyExpression(Integer dayOfMonth, Integer month) {
    if (inputExpressionTf.getValue().isEmpty()) {
      updateCronExpression(DEFAULT_CRON);
    }
    if (dayOfMonth != null && month != null) {
      Map<Integer, String> values = new HashMap<>();
      values.put(3, dayOfMonth.toString());
      values.put(4, "1/" + month.toString());
      values.put(5, "?");
      updateCronExpression(values);
    }
  }

  private void getWeeklyExpression(Set<DayOfWeek> days) {
    if (inputExpressionTf.getValue().isEmpty()) {
      updateCronExpression(DEFAULT_CRON);
    }
    Map<Integer, String> values = new HashMap<>();
    List<String> aux = days.stream().map(d -> String.valueOf(d.getValue())).toList();
    if (days.size() == 7 || days.size() == 0) {
      values.put(5, "*");
    } else {
      values.put(5, String.join(",", aux));
    }
    values.put(3, "?");
    updateCronExpression(values);
  }

  private void getDailyExpression(Units timeUnit, int value) {
    Map<Integer, String> values = new HashMap<>();

    String aux = "*/" + String.valueOf(value);
    if (inputExpressionTf.getValue().isEmpty()) {
      updateCronExpression(DEFAULT_CRON);
    }
    switch (timeUnit) {
      case SECONDS:
        values.put(0, aux);
        values.put(1, "0");
        values.put(2, "0");
        break;
      case MINUTES:
        values.put(0, "0");
        values.put(1, aux);
        values.put(2, "0");
        break;
      case HOURS:
        values.put(0, "0");
        values.put(1, "0");
        values.put(2, aux);
        break;
    }
    updateCronExpression(values);
  }

  private void showNextDates() {
    if (!inputExpressionTf.getValue().isEmpty()) {
      grid.removeAllColumns();
      grid.addColumn(date -> formatDate(date));
      grid.setHeight("108px");
      grid.setItems(q -> generateNextDate(LocalDateTime.now(), q.getOffset(), q.getLimit()));
      add(grid);
    }
  }

  private Stream<LocalDateTime> generateNextDate(LocalDateTime start, int offset, int limit) {
    CronExpression parsedExpression = CronExpression.parse(inputExpressionTf.getValue());
    LocalDateTime firstNext = parsedExpression.next(start);
    if (firstNext == null) {
      return Stream.empty();
    }
    return Stream.iterate(firstNext, Objects::nonNull, parsedExpression::next).skip(offset)
        .limit(limit);
  }

  private String formatDate(LocalDateTime date) {
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.MEDIUM)
        .withLocale(locale == null ? Locale.ENGLISH : locale);
    return date.format(dateFormatter);
  }

  /**
   * Enables or disables user input for the cron expression text field.
   * 
   * @param isEnabled a {@code boolean} to set the editing mode of the cron expression TextField
   */
  public void setCronInputEnabled(boolean isEnabled) {
    cronInputEnabled = isEnabled;
    inputExpressionTf.setReadOnly(!isEnabled);
  }

  /**
   * Displays a button that generates a grid with the next dates scheduled by the generated cron expression.
   * 
   * @param nextDatesVisible a {@code boolean} to set the visibility of the button
   */
  public void setNextDatesButtonVisible(boolean nextDatesVisible) {
    nextDatesBtn.setVisible(nextDatesVisible);
    if (!nextDatesBtn.isVisible()) {
      remove(grid);
    }
  }

  /**
   * Allows the developer to set a default expression.
   * 
   * @param cron a {@code String} representing the expression to be set as default
   * @return a {@code boolean} indicating if the expression is valid or not
   */
  public boolean setDefaultExpression(String cron) {
    if (cron != null && validateCron(cron)) {
      defaultExpression = cron;
      return true;
    } else
      return false;
  }

  /**
   * Sets a list of cron expressions to be displayed if setCommonExpressionsVisible is enabled.
   * 
   * <p>
   * The method validates the cron expressions to be added.
   * 
   * @param crons one or more strings of cron expressions to be validated
   * @return a {@code boolean} indicating if all the cron expressions pass the validation
   */
  public boolean addCommonExpressions(String... crons) {
    if (commonExpressionsList == null) {
      commonExpressionsList = new ArrayList<>();
    }
    for (String cron : crons) {
      if (validateCron(cron)) {
        commonExpressionsList.add(cron);
      } else {
        return false;
      }
    }
    commonExpressionsCb.setItems(commonExpressionsList);
    return true;
  }

  /**
   * Sets visible a ComboBox with common expressions.
   * 
   * <p>
   * When set, a combobox with previously set cron expressions is displayed inside the advanced mode layout allowing the
   * user to choose from one of them.
   * 
   * @param commonExpressionsVisible a {@code boolean} indicating if the ComboBox is displayed or not
   */
  public void setCommonExpressionsVisible(boolean commonExpressionsVisible) {
    this.commonExpressionsVisible = commonExpressionsVisible;
    resetUI();
  }

  /**
   * Sets whether the UI components get a tooltip with helper text for the user.
   * 
   * @param helpEnabled a {@code boolean} indicating if the tooltip is displayed or not
   */
  public void setHelpEnabled(boolean helpEnabled) {
    this.helpEnabled = helpEnabled;
    resetUI();
  }

  @Override
  protected String generateModelValue() {
    return inputExpressionTf.getValue().isEmpty() ? null : inputExpressionTf.getValue();
  }

  @Override
  protected void setPresentationValue(String newPresentationValue) {
    inputExpressionTf.setValue(newPresentationValue);
  }

  /**
   * Sets the {@link Locale} for this component.
   *
   * @param locale the {@link Locale} to be used
   */
  public void setLocale(Locale locale) {
    this.locale = locale;
  }

  /**
   * Sets the internationalization settings for the field.
   * <p>
   * If {@code i18n} is {@code null}, default internationalization to english.
   * </p>
   *
   * @param i18n the {@link CronExpressionFieldI18n} to use, or {@code null} for default
   */
  public void setI18n(CronExpressionFieldI18n i18n) {
    this.i18n = (i18n != null) ? i18n : CronExpressionFieldI18n.createDefault();
    getUI().ifPresent(ui -> setI18nWithJS());
  }

  private void setI18nWithJS() {
    runBeforeClientResponse(ui -> {
      JsonObject i18nObject = (JsonObject) JsonSerializer.toJson(i18n);
      for (String key : i18nObject.keys()) {
        getElement().executeJs("this.set('i18n." + key + "', $0)", i18nObject.get(key));
      }
    });
  }

  private void runBeforeClientResponse(SerializableConsumer<UI> command) {
    getElement().getNode().runWhenAttached(ui -> ui.beforeClientResponse(this, context -> command.accept(ui)));
  }

}
