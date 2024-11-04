package com.upn.contactsapp.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;
import com.upn.contactsapp.AppDatabase;
import com.upn.contactsapp.R;
import com.upn.contactsapp.daos.ContactDAO;
import com.upn.contactsapp.entities.Contact;
import com.upn.contactsapp.services.ContactService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CreateContactActivity extends AppCompatActivity {

    ImageView ivPhoto;
    String imageBase64;
    ContactService service;
    List<Contact> contactList = new ArrayList<>();
    int currentPage = 0;
    final int pageSize = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_contact);

        ivPhoto = findViewById(R.id.ivPhoto);
        setUpBtnTakePhoto();
        setUpBtnChoosePhoto();

        AppDatabase db = AppDatabase.getInstance(this);
        ContactDAO contactDAO = db.contactDAO();

        Button btnGuardarContacto = findViewById(R.id.btnGuardarContacto);
        EditText etName = findViewById(R.id.etName);
        EditText etPhone = findViewById(R.id.etPhone);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://66d5b903f5859a7042673752.mockapi.io")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        service = retrofit.create(ContactService.class);

        btnGuardarContacto.setOnClickListener(view -> {
            String name = etName.getText().toString();
            String phone = etPhone.getText().toString();

            // Validar entradas
            if (name.isEmpty() || phone.isEmpty()) {
                showToast("Por favor, complete todos los campos");
                return;
            }

            Contact contact = new Contact(name, phone);
            contact.image = imageBase64;

            // Guardar en la base de datos local
            contact.localId = (int) contactDAO.insert(contact);
            Log.i("CONTACT_LOCAL_ID", String.valueOf(contact.localId));

            // Crear contacto en el servidor
            service.create(contact).enqueue(new Callback<Contact>() {
                @Override
                public void onResponse(Call<Contact> call, Response<Contact> response) {
                    if (response.isSuccessful()) {
                        Contact newContact = response.body();
                        Intent intent = getIntent();
                        intent.putExtra("CONTACT", new Gson().toJson(newContact));
                        contact.id = newContact.id;
                        contactDAO.update(contact.localId, newContact.id);
                        setResult(RESULT_OK, intent);
                        finish();
                    } else {
                        Log.e("MAIN_APP", "Error en la respuesta: " + response.message());
                    }
                }

                @Override
                public void onFailure(Call<Contact> call, Throwable throwable) {
                    Log.e("MAIN_APP", throwable.getMessage());
                    showToast("Error al crear el contacto");
                }
            });
        });

        loadContacts();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void loadContacts() {
        service.getContacts(currentPage, pageSize).enqueue(new Callback<List<Contact>>() {
            @Override
            public void onResponse(Call<List<Contact>> call, Response<List<Contact>> response) {
                if (response.isSuccessful()) {
                    List<Contact> newContacts = response.body();
                    if (newContacts != null) {
                        contactList.addAll(newContacts);
                        Log.i("CONTACTS_LOADED", "Cargados " + newContacts.size() + " contactos");
                    }
                } else {
                    Log.e("MAIN_APP", "Error en la respuesta: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<List<Contact>> call, Throwable throwable) {
                Log.e("MAIN_APP", throwable.getMessage());
            }
        });
    }

    private void setUpBtnChoosePhoto() {
        Button btnChoosePhoto = findViewById(R.id.btnChoosePhoto);
        btnChoosePhoto.setOnClickListener(view -> openPhotoGallery());
    }

    private void setUpBtnTakePhoto() {
        Button btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnTakePhoto.setOnClickListener(view -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
            }
        });
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, 100);
    }

    private void openPhotoGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, 101);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == 100) {
                Bundle extras = data.getExtras();
                Bitmap imageBitmap = (Bitmap) extras.get("data");
                setImageAndEncode(imageBitmap);
            } else if (requestCode == 101) {
                Uri selectedImage = data.getData();
                ivPhoto.setImageURI(selectedImage);
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                    setImageAndEncode(bitmap);
                } catch (IOException e) {
                    Log.e("CreateContactActivity", "Error al obtener la imagen", e);
                }
            }
        }
    }

    private void setImageAndEncode(Bitmap bitmap) {
        ivPhoto.setImageBitmap(bitmap);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        imageBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT);
    }
}
