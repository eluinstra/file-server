/**
 * Copyright 2020 E.Luinstra
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bitbucket.eluinstra.fs.service.web;

import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.extensions.markup.html.form.DateTextField;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.form.FormComponentPanel;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

import lombok.AccessLevel;
import lombok.val;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BootstrapDateTimePicker extends FormComponentPanel<Date>
{
	public enum Type
	{
		DATE_TIME, DATE, TIME;
	}
	public enum HourFormat
	{
		H12, H24;
	}

	private static final long serialVersionUID = 1L;
	String format;
	String formatJS;
	@NonFinal
	Type type;
	HourFormat hourFormat;
	@NonFinal
	Date startDate;
	@NonFinal
	Date endDate;
	@NonFinal
	Date dateTime;
	TextField<Date> dateTimeField;

	public BootstrapDateTimePicker(final String id)
	{
		this(id,(IModel<Date>)null);
	}

	public BootstrapDateTimePicker(final String id, final String format, final Type type)
	{
		this(id,null,format,type);
	}
	
	public BootstrapDateTimePicker(final String id, final IModel<Date> model)
	{
		this(id,model,"dd-MM-yyyy HH:mm:ss",Type.DATE_TIME);
	}

	public BootstrapDateTimePicker(final String id, final IModel<Date> model, final String format, final Type type)
	{
		super(id,model);
		this.format = format;
		this.hourFormat = format.contains("H") ? HourFormat.H24 : HourFormat.H12;
		this.formatJS = format.replaceAll("H","h");
		setType(type);
		
		val dateTimePicker = new MarkupContainer("dateTimePicker")
		{
			private static final long serialVersionUID = 1L;
		};
		dateTimePicker.setMarkupId(getDateTimePickerId());
		dateTimePicker.setOutputMarkupId(true);
		add(dateTimePicker);

		dateTimeField = new DateTextField("dateTime",new PropertyModel<>(this,"dateTime"),format)
		{
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isRequired()
			{
				return BootstrapDateTimePicker.this.isRequired();
			}
		};
		dateTimePicker.add(dateTimeField);
	}
	
	public static String getLinkBootstrapDateTimePickersJavaScript(final BootstrapDateTimePicker startDate, final BootstrapDateTimePicker endDate)
	{
		return
			"$(function () {" +
				"$('#" + startDate.getDateTimePickerId() + "').on('changeDate',function () {" +
					"var d = $('#" + startDate.getDateTimePickerId() + "').data('datetimepicker').getDate();" +
					"d.setDate(d.getDate() + 1);" +
				  "$('#" + endDate.getDateTimePickerId() + "').data('datetimepicker').setStartDate(d);" +
				"});" +
				"$('#" + endDate.getDateTimePickerId() + "').on('changeDate',function (e) {" +
					"var d = $('#" + endDate.getDateTimePickerId() + "').data('datetimepicker').getDate();" +
					"d.setDate(d.getDate() - 1);" +
				  "$('#" + startDate.getDateTimePickerId() + "').data('datetimepicker').setEndDate(d);" +
				"});" +
		"});";
	}
	
	@Override
	public void renderHead(final IHeaderResponse response)
	{
		val options = new ArrayList<>();
		if (formatJS != null)
			options.add("format: '" + formatJS + "'");
		if (!Type.DATE_TIME.equals(type) & !Type.DATE.equals(type))
			options.add("pickDate: false");
		if (!Type.DATE_TIME.equals(type) & !Type.TIME.equals(type))
			options.add("pickTime: false");
		if (HourFormat.H12.equals(hourFormat))
			options.add("pick12HourFormat: true");
		if (getJQueryLocale() != null)
			options.add("language: '" + getLocale().toString() + "'");
		if (startDate != null)
			options.add("startDate: new Date(" + startDate.getTime() + ")");
		if (endDate != null)
			options.add("endDate: new Date(" + endDate.getTime() + ")");
		response.render(OnDomReadyHeaderItem.forScript("$(function () {$('#" + getDateTimePickerId() + "').datetimepicker({" + StringUtils.join(options,",") + "});});"));
		super.renderHead(response);
	}
	
	@Override
	public void convertInput()
	{
		dateTime = dateTimeField.getConvertedInput();
		setConvertedInput(dateTime);
	}
	
	@Override
	protected void onBeforeRender()
	{
		dateTime = getModelObject();
		super.onBeforeRender();
	}
	
	public String getDateFormat()
	{
		return format;
	}
	
	public BootstrapDateTimePicker setType(final Type type)
	{
		this.type = type;
		return this;
	}
	
	public JQueryLocale getJQueryLocale()
	{
		return JQueryLocale.EN;
	}

	public void setStartDate(final Date startDate)
	{
		this.startDate = startDate;
	}

	public void setEndDate(final Date endDate)
	{
		this.endDate = endDate;
	}
	
	private String getDateTimePickerId()
	{
		return getMarkupId() + "DateTimePicker";
	}
	
	public Date getDateTime()
	{
		return dateTime;
	}
	
	public void setDateTime(final Date dateTime)
	{
		this.dateTime = dateTime;
	}
}