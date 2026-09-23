exports.handler = async (event) => {
  const phone = event.request.userAttributes.phone_number;
  const email = event.request.userAttributes.email;
  if (phone && !email) {
    event.response.autoConfirmUser = true;
    event.response.autoVerifyPhone = true;
  }
  return event;
};
