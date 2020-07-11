'use strict';

const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// Listens for new messages added to /chats/:chatId/messages and
// sends a notification to /users/:userId
exports.sendMessageNotification = functions.firestore.document('/chats/{chatId}/messages/{messageId}')
    .onCreate(async (snap, context) => {
	  const message = snap.data();
      const senderId = message.senderId;
	  const chatId = context.params.chatId;
	  //const messageId = context.params.messageId;
	  
	  const chat = await admin.firestore().collection('chats').doc(chatId).get();
	  let userIds = chat.data().userIds;
	  userIds = userIds.filter(e => e !== senderId);
	  
	  let participants = [];
	  let users = await admin.firestore().collection('users').where('id', 'in', userIds).get();
		users.forEach(doc => {
		  //console.log(doc.id, '=>', doc.data());
		  participants.push(doc.data());
		});

	  participants = participants.filter(u => u.status !== 1);//!online
	  const tokens = participants.map(u => u.fcmToken);
	  
	  if (tokens.length === 0) return;
      
      // Notification details.
      const payload = {
        data: {
          senderId: senderId,
		  chatId: chatId,
          message: message.text
        }
      };
	  
      // Send notifications to all tokens.
      const response = await admin.messaging().sendToDevice(tokens, payload);
	  
      // For each message check if there was an error.
      response.results.forEach((result, index) => {
        const error = result.error;
        if (error) {
          console.error('Failure sending notification to', participants[index].id, error);
        }
      });
    });