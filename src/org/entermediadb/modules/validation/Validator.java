/* Generated by Together */

package org.entermediadb.modules.validation;

import java.util.Date;
import java.util.Map;

import org.openedit.data.PropertyDetail;

public class Validator {

	public String findError(PropertyDetail detail, String value) {
		if (detail != null) {
			if (value == null || value.length() == 0) {
				if (detail.isRequired()) {
					return "error-required";
				}
				return null;
			} else {
				if (detail.get("regex") != null) {
					String regex = detail.get("regex");
					if (!value.matches(regex)) {
						return "error-regex";
					}
				}

				String type = detail.getDataType();
				if (type == null) {
					return null;
				}
				if ("email".equals(type)) {
					if (value != null && value.indexOf("@") > -1 && value.indexOf(".") > -1) {
						return "error-" + type;
					}
				}
				if ("integer".equals(type)) {
					if (value != null) {
						try {
							Integer.parseInt(value);
							return null;
						} catch (NumberFormatException ex) {
							return "error-" + type;
						}
					}
				}
				if (detail.isDate()) {
					// TODO: We cant validate unless they pass in the format the
					// UI is using
//					if (detail.getDateFormat() != null) {
//						try {
//							Date parsed = detail.getDateFormat().parse(value);
//							if (!detail.getDateFormat().parse(detail.getDateFormat().format(parsed)).equals(parsed)) {
//								throw new Exception();
//							}
//							return null;
//						} catch (Exception ex) {
//							return "error-format";
//							// return "(" +
//							// detail.getDateFormatString().toLowerCase() + ")";
//						}
//					}
				}
			}
		}
		return null;

	}

	public String findSuccess(PropertyDetail detail, String value) {
		if (detail.isDate()) {
			// try {
			// // this is silly - we should no need to use an
			// // exception, but I don't see an
			// // isformatable or canformat type method.. Hmm..
			// detail.getDateFormat().parse(value);
			// return "(" + detail.getDateFormatString().toLowerCase() + ")";
			// } catch (Exception ex) {
			// return null;
			// }
		}
		return null;
	}

	public String findInfo(PropertyDetail detail, String value) {

		return null;
	}

	public void validateDetail(Map errors, Map successes, Map infos, String value, PropertyDetail detail) {
		if (detail != null) {
			String error = findError(detail, value); // these show that the
														// entered data is not
														// valid
			if (error != null) {
				errors.put(detail.getId(), error);
			} else {
				String success = findSuccess(detail, value); // these let the
																// user know
																// they entered
																// correctly
				if (success != null) {
					successes.put(detail.getId(), success);
				}
			}

			String info = findInfo(detail, value); // these tip the user before
													// any data is entered
			if (info != null) {
				infos.put(detail.getId(), info);
			}
		}
	}

}
